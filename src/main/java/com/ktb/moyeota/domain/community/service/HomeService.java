package com.ktb.moyeota.domain.community.service;

import com.ktb.moyeota.domain.community.dto.MapPinItem;
import com.ktb.moyeota.domain.community.dto.MapPinSearchRequest;
import com.ktb.moyeota.domain.community.dto.MapPinSearchResponse;
import com.ktb.moyeota.domain.community.dto.MapPinType;
import com.ktb.moyeota.domain.community.dto.NearbyPostAuthor;
import com.ktb.moyeota.domain.community.dto.NearbyPostCursor;
import com.ktb.moyeota.domain.community.dto.NearbyPostItem;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchRequest;
import com.ktb.moyeota.domain.community.dto.NearbyPostSearchResponse;
import com.ktb.moyeota.domain.community.exception.CommunityErrorCode;
import com.ktb.moyeota.domain.community.repository.CommunityNearbyProjection;
import com.ktb.moyeota.domain.community.repository.CommunityPinProjection;
import com.ktb.moyeota.domain.community.repository.CommunityPostRepository;
import com.ktb.moyeota.domain.community.service.cache.CellPins;
import com.ktb.moyeota.domain.community.service.cache.MapPinCellCache;
import com.ktb.moyeota.domain.community.service.cache.MapPinCellLoader;
import com.ktb.moyeota.domain.community.service.grid.GridCell;
import com.ktb.moyeota.domain.community.service.grid.MapGrid;
import com.ktb.moyeota.domain.companionpost.repository.CompanionNearbyProjection;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPinProjection;
import com.ktb.moyeota.domain.companionpost.repository.CompanionPostRepository;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.global.common.Viewport;
import com.ktb.moyeota.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final int PIN_LIMIT = 500;
    private static final int PIN_FETCH_LIMIT = PIN_LIMIT + 1;
    private static final int NEARBY_PAGE_SIZE = 10;

    private static final BigDecimal MAX_VIEWPORT_SPAN_DEGREES = BigDecimal.valueOf(1.0);
    private static final Comparator<MapPinItem> PIN_ORDER =
            Comparator.comparing(MapPinItem::type).thenComparing(MapPinItem::id);

    private final CommunityPostRepository communityPostRepository;
    private final CompanionPostRepository companionPostRepository;
    private final NearbyPostCursorCodec cursorCodec;
    private final ImageUrlResolver imageUrlResolver;
    private final MapGrid mapGrid;
    private final MapPinCellCache mapPinCellCache;
    private final MapPinCellLoader mapPinCellLoader;

    public MapPinSearchResponse searchMapPins(MapPinSearchRequest request) {
        validateViewport(request.swLat(), request.swLng(), request.neLat(), request.neLng());
        Viewport viewport = new Viewport(request.swLat(), request.swLng(), request.neLat(), request.neLng());
        List<GridCell> cells = mapGrid.cellsCovering(viewport);

        Optional<Map<GridCell, CellPins>> cached = mapPinCellCache.readCells(cells);
        if (cached.isEmpty()) {
            return searchViewport(viewport);
        }

        List<MapPinItem> items = new ArrayList<>();
        List<GridCell> missed = new ArrayList<>();
        boolean partlyOverflowed = false;
        for (GridCell cell : cells) {
            CellPins pins = cached.get().get(cell);
            if (pins == null) {
                missed.add(cell);
                continue;
            }
            CellVerdict verdict = collect(cell, pins, viewport, items);
            if (verdict == CellVerdict.EXCEEDED) {
                return exceeded();
            }
            partlyOverflowed |= verdict == CellVerdict.PARTLY_OVERFLOWED;
        }
        if (partlyOverflowed) {
            return searchViewport(viewport);
        }

        for (GridCell cell : missed) {
            CellPins pins = mapPinCellLoader.load(cell);
            mapPinCellCache.writeCell(cell, pins);
            CellVerdict verdict = collect(cell, pins, viewport, items);
            if (verdict == CellVerdict.EXCEEDED) {
                return exceeded();
            }
            if (verdict == CellVerdict.PARTLY_OVERFLOWED) {
                return searchViewport(viewport);
            }
        }

        items.sort(PIN_ORDER);
        return new MapPinSearchResponse(items, PIN_LIMIT, false);
    }

    private CellVerdict collect(GridCell cell, CellPins pins, Viewport viewport, List<MapPinItem> items) {
        if (pins.overflow()) {
            return mapGrid.isFullyInside(cell, viewport) ? CellVerdict.EXCEEDED : CellVerdict.PARTLY_OVERFLOWED;
        }
        for (MapPinItem pin : pins.pins()) {
            if (isInside(pin, viewport)) {
                items.add(pin);
            }
        }
        return items.size() > PIN_LIMIT ? CellVerdict.EXCEEDED : CellVerdict.COLLECTED;
    }

    private MapPinSearchResponse searchViewport(Viewport viewport) {
        List<CommunityPinProjection> communityPins = communityPostRepository.findPinsInViewport(
                viewport.swLat(), viewport.swLng(), viewport.neLat(), viewport.neLng(), PIN_FETCH_LIMIT);
        List<CompanionPinProjection> companionPins = companionPostRepository.findPinsInViewport(
                viewport.swLat(), viewport.swLng(), viewport.neLat(), viewport.neLng(), PIN_FETCH_LIMIT);

        List<MapPinItem> items = new ArrayList<>(communityPins.size() + companionPins.size());
        for (CommunityPinProjection pin : communityPins) {
            items.add(new MapPinItem(MapPinType.COMMUNITY, pin.getId(), pin.getLat(), pin.getLng()));
        }
        for (CompanionPinProjection pin : companionPins) {
            items.add(new MapPinItem(MapPinType.COMPANION, pin.getId(), pin.getOriginLat(), pin.getOriginLng()));
        }

        if (items.size() > PIN_LIMIT) {
            return exceeded();
        }
        items.sort(PIN_ORDER);
        return new MapPinSearchResponse(items, PIN_LIMIT, false);
    }

    private static boolean isInside(MapPinItem pin, Viewport viewport) {
        return viewport.swLat().compareTo(pin.lat()) <= 0 && pin.lat().compareTo(viewport.neLat()) <= 0
                && viewport.swLng().compareTo(pin.lng()) <= 0 && pin.lng().compareTo(viewport.neLng()) <= 0;
    }

    private static MapPinSearchResponse exceeded() {
        return new MapPinSearchResponse(List.of(), PIN_LIMIT, true);
    }

    @Transactional(readOnly = true)
    public NearbyPostSearchResponse searchNearbyPosts(NearbyPostSearchRequest request) {
        validateViewport(request.swLat(), request.swLng(), request.neLat(), request.neLng());

        NearbyPostCursor cursor = cursorCodec.decode(request.cursor());
        NearbyPostCursor.TableCursor communityCursor = cursor.community();
        NearbyPostCursor.TableCursor companionCursor = cursor.companion();

        List<CommunityNearbyProjection> communityRows = communityPostRepository.findNearby(
                request.lat(), request.lng(), request.swLat(), request.swLng(), request.neLat(), request.neLng(),
                communityCursor == null ? null : communityCursor.distance(),
                communityCursor == null ? null : communityCursor.id(),
                NEARBY_PAGE_SIZE);
        List<CompanionNearbyProjection> companionRows = companionPostRepository.findNearby(
                request.lat(), request.lng(), request.swLat(), request.swLng(), request.neLat(), request.neLng(),
                companionCursor == null ? null : companionCursor.distance(),
                companionCursor == null ? null : companionCursor.id(),
                NEARBY_PAGE_SIZE);

        List<Candidate> merged = new ArrayList<>(communityRows.size() + companionRows.size());
        for (CommunityNearbyProjection row : communityRows) {
            merged.add(Candidate.ofCommunity(row, imageUrlResolver.toUrl(row.getAuthorProfileImageUrl())));
        }
        for (CompanionNearbyProjection row : companionRows) {
            merged.add(Candidate.ofCompanion(row, imageUrlResolver.toUrl(row.getHostProfileImageUrl())));
        }
        merged.sort(Comparator.comparing(Candidate::distance)
                .thenComparing(Candidate::createdAt, Comparator.reverseOrder()));

        List<Candidate> page = merged.size() > NEARBY_PAGE_SIZE
                ? merged.subList(0, NEARBY_PAGE_SIZE)
                : merged;

        NearbyPostCursor.TableCursor nextCommunityCursor = lastConsumed(page, MapPinType.COMMUNITY)
                .map(c -> new NearbyPostCursor.TableCursor(c.distance(), c.id()))
                .orElse(communityCursor);
        NearbyPostCursor.TableCursor nextCompanionCursor = lastConsumed(page, MapPinType.COMPANION)
                .map(c -> new NearbyPostCursor.TableCursor(c.distance(), c.id()))
                .orElse(companionCursor);

        boolean hasNext = merged.size() > page.size()
                || communityRows.size() == NEARBY_PAGE_SIZE
                || companionRows.size() == NEARBY_PAGE_SIZE;
        String nextCursor = hasNext
                ? cursorCodec.encode(new NearbyPostCursor(nextCommunityCursor, nextCompanionCursor))
                : null;

        List<NearbyPostItem> items = page.stream().map(Candidate::item).toList();
        return new NearbyPostSearchResponse(items, nextCursor);
    }

    private void validateViewport(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {
        if (swLat.compareTo(neLat) >= 0 || swLng.compareTo(neLng) >= 0) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_VIEWPORT_OUT_OF_RANGE);
        }
        BigDecimal latSpan = neLat.subtract(swLat);
        BigDecimal lngSpan = neLng.subtract(swLng);
        if (latSpan.compareTo(MAX_VIEWPORT_SPAN_DEGREES) > 0 || lngSpan.compareTo(MAX_VIEWPORT_SPAN_DEGREES) > 0) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_VIEWPORT_TOO_LARGE);
        }
    }

    private Optional<Candidate> lastConsumed(List<Candidate> page, MapPinType type) {
        Candidate last = null;
        for (Candidate candidate : page) {
            if (candidate.type() == type) {
                last = candidate;
            }
        }
        return Optional.ofNullable(last);
    }

    private enum CellVerdict {
        COLLECTED, EXCEEDED, PARTLY_OVERFLOWED
    }

    private record Candidate(MapPinType type, Long id, double distance, LocalDateTime createdAt, NearbyPostItem item) {

        static Candidate ofCommunity(CommunityNearbyProjection row, String authorProfileImageUrl) {
            NearbyPostAuthor author = new NearbyPostAuthor(row.getAuthorNickname(), authorProfileImageUrl);
            NearbyPostItem item = NearbyPostItem.ofCommunity(
                    row.getId(), row.getTitle(), author, row.getDistanceM(),
                    row.getCommentCount(), row.getCreatedAt());
            return new Candidate(MapPinType.COMMUNITY, row.getId(), row.getDistanceM(), row.getCreatedAt(), item);
        }

        static Candidate ofCompanion(CompanionNearbyProjection row, String hostProfileImageUrl) {
            NearbyPostAuthor author = new NearbyPostAuthor(row.getHostNickname(), hostProfileImageUrl);
            String title = row.getOriginName() + " → " + row.getDestName();
            boolean isExpired = !"RECRUITING".equals(row.getStatus());
            NearbyPostItem item = NearbyPostItem.ofCompanion(
                    row.getId(), title, author, row.getDistanceM(),
                    row.getTransportType(), row.getCurrentCount(), row.getCapacity(),
                    row.getDepartureAt(), isExpired);
            return new Candidate(MapPinType.COMPANION, row.getId(), row.getDistanceM(), row.getCreatedAt(), item);
        }
    }
}
