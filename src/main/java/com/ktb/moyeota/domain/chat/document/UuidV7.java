package com.ktb.moyeota.domain.chat.document;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class UuidV7 {

    private static final long VERSION = 0x7000L;
    private static final long VARIANT = 0x8000_0000_0000_0000L;
    private static final long RAND_A_MASK = 0x0FFFL;
    private static final long RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL;

    private UuidV7() {
    }

    public static UUID at(Instant instant) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long mostSigBits = (instant.toEpochMilli() << 16) | VERSION | (random.nextLong() & RAND_A_MASK);
        long leastSigBits = VARIANT | (random.nextLong() & RAND_B_MASK);
        return new UUID(mostSigBits, leastSigBits);
    }
}
