#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

OUT=${1:-docs/matching-sim/places.json}
BASE_URL=${KAKAO_LOCAL_BASE_URL:-https://dapi.kakao.com}
HALF_SIDE_M=${HALF_SIDE_M:-1500}
MIN_SIDE_M=${MIN_SIDE_M:-60}
PAGE_LIMIT=45

if [ -z "${KAKAO_REST_API_KEY:-}" ] && [ -f .env ]; then
  KAKAO_REST_API_KEY=$(grep -E '^KAKAO_REST_API_KEY=' .env | tail -1 | cut -d= -f2- | tr -d '"'"'"' \r' || true)
fi
if [ -z "${KAKAO_REST_API_KEY:-}" ]; then
  echo "KAKAO_REST_API_KEY 가 없습니다. .env 에 KAKAO_REST_API_KEY=... 로 넣어 주세요" >&2
  exit 1
fi

AREAS="판교:37.4040:127.1040
강남역:37.4979:127.0276
가산:37.4810:126.8900
마곡:37.5630:126.8320"
CATEGORIES="SW8 CT1 AT4 PO3 SC4 HP8 MT1 AD5 FD6 CE7"
KEYWORDS="타워 빌딩 센터 지식산업센터 본사 사옥 연구소 캠퍼스 밸리 테크"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
RAW=$TMP/raw.jsonl
: > "$RAW"
CALLS=0
TRUNCATED=0

log() { echo "$(date +%H:%M:%S) $*" >&2; }

request() {
  local kind=$1 value=$2 rect=$3 page=$4 attempt=0 status
  while :; do
    if [ "$kind" = category ]; then
      status=$(curl -sS -o "$TMP/res.json" -w '%{http_code}' --get "$BASE_URL/v2/local/search/category.json" \
        -H "Authorization: KakaoAK $KAKAO_REST_API_KEY" \
        --data-urlencode "category_group_code=$value" --data-urlencode "rect=$rect" \
        --data-urlencode "page=$page" --data-urlencode "size=15")
    else
      status=$(curl -sS -o "$TMP/res.json" -w '%{http_code}' --get "$BASE_URL/v2/local/search/keyword.json" \
        -H "Authorization: KakaoAK $KAKAO_REST_API_KEY" \
        --data-urlencode "query=$value" --data-urlencode "rect=$rect" \
        --data-urlencode "page=$page" --data-urlencode "size=15")
    fi
    CALLS=$((CALLS + 1))
    [ "$status" = 200 ] && return 0
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 3 ] || { [ "$status" != 429 ] && [ "${status:0:1}" != 5 ]; }; then
      log "요청 실패 $status ($kind $value $rect page $page): $(cat "$TMP/res.json")"
      exit 1
    fi
    sleep $((attempt * 2))
  done
}

append_documents() {
  local area=$1
  jq -c --arg area "$area" '.documents[] | {
      id,
      name: .place_name,
      category: (if .category_group_code == "" then null else .category_group_code end),
      roadAddress: (if .road_address_name == "" then null else .road_address_name end),
      lat: (.y | tonumber * 1e6 | round / 1e6),
      lng: (.x | tonumber * 1e6 | round / 1e6),
      area: $area
    }' "$TMP/res.json" >> "$RAW"
}

side_m() {
  awk -v x1="$1" -v y1="$2" -v x2="$3" -v y2="$4" 'BEGIN {
    pi = atan2(0, -1); lat = (y1 + y2) / 2
    w = (x2 - x1) * 111320 * cos(lat * pi / 180); h = (y2 - y1) * 111320
    printf "%d", (w > h ? w : h)
  }'
}

collect_tile() {
  local area=$1 kind=$2 value=$3 x1=$4 y1=$5 x2=$6 y2=$7
  local rect="$x1,$y1,$x2,$y2" total page xm ym
  request "$kind" "$value" "$rect" 1
  total=$(jq '.meta.total_count' "$TMP/res.json")
  [ "$total" -eq 0 ] && return 0

  if [ "$total" -gt "$PAGE_LIMIT" ] && [ "$(side_m "$x1" "$y1" "$x2" "$y2")" -gt "$MIN_SIDE_M" ]; then
    xm=$(awk -v a="$x1" -v b="$x2" 'BEGIN { printf "%.7f", (a + b) / 2 }')
    ym=$(awk -v a="$y1" -v b="$y2" 'BEGIN { printf "%.7f", (a + b) / 2 }')
    collect_tile "$area" "$kind" "$value" "$x1" "$y1" "$xm" "$ym"
    collect_tile "$area" "$kind" "$value" "$xm" "$y1" "$x2" "$ym"
    collect_tile "$area" "$kind" "$value" "$x1" "$ym" "$xm" "$y2"
    collect_tile "$area" "$kind" "$value" "$xm" "$ym" "$x2" "$y2"
    return 0
  fi

  [ "$total" -gt "$PAGE_LIMIT" ] && TRUNCATED=$((TRUNCATED + 1))
  append_documents "$area"
  page=1
  while [ "$(jq '.meta.is_end' "$TMP/res.json")" = false ] && [ "$page" -lt 3 ]; do
    page=$((page + 1))
    request "$kind" "$value" "$rect" "$page"
    append_documents "$area"
  done
}

while IFS=: read -r area lat lng; do
  read -r x1 y1 x2 y2 <<< "$(awk -v lat="$lat" -v lng="$lng" -v half="$HALF_SIDE_M" 'BEGIN {
    pi = atan2(0, -1); dlat = half / 111320; dlng = half / (111320 * cos(lat * pi / 180))
    printf "%.7f %.7f %.7f %.7f", lng - dlng, lat - dlat, lng + dlng, lat + dlat
  }')"
  for code in $CATEGORIES; do
    collect_tile "$area" category "$code" "$x1" "$y1" "$x2" "$y2"
  done
  for keyword in $KEYWORDS; do
    collect_tile "$area" keyword "$keyword" "$x1" "$y1" "$x2" "$y2"
  done
  log "$area 완료 (누적 호출 $CALLS)"
done <<< "$AREAS"

mkdir -p "$(dirname "$OUT")"
jq -s 'unique_by(.id) | sort_by(.area, .name)' "$RAW" > "$OUT"
log "장소 $(jq length "$OUT")개 → $OUT (호출 $CALLS, 45건에서 잘린 칸 $TRUNCATED)"
jq -r 'group_by(.area)[] | "\(.[0].area)\t\(length)"' "$OUT" >&2
