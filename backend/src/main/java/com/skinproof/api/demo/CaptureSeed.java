package com.skinproof.api.demo;

import java.time.Instant;

record CaptureSeed(Instant takenAt, boolean baseline, double rawScore) {
}
