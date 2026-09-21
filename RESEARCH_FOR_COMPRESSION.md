# Compression research: “Best” preset (~85% size reduction)

This document supports the **Best** quality preset: original resolution, AV1 when available, 30 fps cap, AAC audio, target output ≈ **15%** of source bytes (`sizeRatio = 0.15`). It is the approval artifact for Phase 2 implementation in this repo.

---

## 1. Executive summary

**Can Android Media3 hit ~85% savings at full resolution with acceptable quality?** Often **yes for large, high-bitrate sources** (phone 4K/60, screen recordings, lightly compressed exports). It is **not guaranteed** for sources already efficiently encoded (HEVC/AV1 at moderate bitrate) or very short clips: the encoder has **minimum practical bitrates**, and the planner may **lower fps, audio bitrate, or resolution** to approach an aggressive target.

**How we hit the goal on-device:** This app does **not** use CRF or VMAF. It uses **target file size** → derived **video bitrate** (`VideoEncodePlanner.targetBitrateFromSize`), plus **codec**, **fps cap**, and **resolution** from presets and `autoAdjustForTargetMb`. The Best preset combines **AV1** (when hardware exposes an encoder), **30 fps**, **~160 kbps AAC**, and **`sizeRatio 0.15`**.

**AV1 fallback:** If `VIDEO_AV1` is missing or fails capability checks, the pipeline falls back **AV1 → H.265 → H.264** (`VideoCompressionPlanner`, `VideoCodecMime.pickEfficientMime`).

**48 kHz AAC:** Media3 `AudioEncoderSettings` exposes **bitrate and profile**, not an explicit sample-rate field in our integration. **Passthrough/re-encode keeps the source sample rate** unless we add a resampler. For sharing, **128–160 kbps AAC** at 44.1 or 48 kHz is typically transparent; forcing 48 kHz is **optional** (nice-to-have), not required for v1.

---

## 2. Terminology

| Term | Meaning in this app |
|------|---------------------|
| **85% reduction** | Output size ≈ **15%** of original bytes. |
| **`sizeRatio`** | `QualityPresetConfig.sizeRatio`: multiplier on original size for target MB (`targetMb = originalMb × sizeRatio`). **0.15** ⇒ ~85% reduction if the planner can honor it. |
| **Target-size budgeting** | Solve video bitrate from `(targetBytes − audio − overhead) / duration`, clamp to min/max (`VideoEncodePlanner`). |
| **CRF / VMAF** | Desktop/ffmpeg quality models; **not** exposed by Android `MediaCodec` / Media3 Transformer for our path. |

---

## 3. Codec research (external)

### Efficiency at matched quality

Independent comparisons (VMAF, BD-rate, multi-codec datasets) generally rank **AV1 > HEVC (H.265) > H.264** at the same perceptual quality:

- Typical **~20–40%** extra bitrate savings for AV1 vs HEVC on mixed real content; wider on clean/animation, narrower on noisy sports ([ForaSoft codec comparison](https://www.forasoft.com/learn/video-quality/articles-vqm/codec-comparison-real-content), [Bitmovin multi-codec study](https://bitmovin.com/blog/av1-multi-codec-dash-dataset/)).
- Community re-encode reports (e.g. r/AV1) often cite **~10–33%** smaller than x265 at similar subjective quality; highly content-dependent.

### Implication for 85% total reduction

**Codec alone is insufficient** if the source is already HEVC at a sane bitrate: you still need a **much lower target bitrate** (and often **fps cap**). Best preset uses **both** AV1 efficiency and **0.15 sizeRatio**.

---

## 4. Android / Media3 constraints

- **Transformer** encodes via **hardware MediaCodec**. Control: **mime type**, **resolution**, **frame rate**, **`VideoEncoderSettings` bitrate** — not x264-style `-crf` ([Android Transformer](https://developer.android.com/media/media3/transformer)).
- **AV1** only when the device lists a working **HW encoder** (`VideoCodecMime.hasEncoder`, `VideoCompressionPlanner.isCodecSupported`).
- **Target size** implementation matches Josh Atticus Compressor: `VideoEncodePlanner.targetBitrateFromSize` and `autoAdjustForTargetMb` when min bitrate exceeds target.

---

## 5. Bitrate / quality targeting

### Kush Gauge (rule of thumb)

Rough H.264 budget: `width × height × fps × motion_rank × 0.07` bps. Coefficients differ per codec; use only as a sanity check, not as our primary planner.

### VMAF on mobile

Broadband/phone viewing often targets **VMAF ~90–93** on phone models vs stricter TV targets ([VMAF quality budget](https://www.forasoft.com/learn/video-quality/articles-vqm/setting-a-quality-target)). We do **not** run VMAF on-device in v1.

### Recommended approach (this app)

Keep **target-size + auto-adjust**; tune **sizeRatio**, **fps**, **codec**, **audio bitrate**. Optional future: probe encode and compare output size.

### High-fps sources (e.g. 120 fps)

Capping to **30 fps** removes redundant temporal information for sharing with small perceptual cost; aligns with Best (`targetFps = 30`) and existing `autoAdjustForTargetMb` (fps stepped down before resolution).

---

## 6. Audio: AAC @ 48 kHz

| Rate (stereo AAC-LC) | Typical use |
|----------------------|-------------|
| 96 kbps | Aggressive; acceptable for speech/light music |
| 128 kbps | Common “good enough” for social sharing |
| 160–192 kbps | Safer for music/complex stereo |

**Best preset:** **160 kbps AAC** (`QualityPresetConfig.defaultBest`) balances transparency and bytes at 15% video budget.

**Sample rate:** Without a dedicated resample step, output audio rate follows **source or encoder default**. Accept **44.1/48 kHz passthrough** for v1; document 48 kHz as a future enhancement if we add explicit resampling in the Media3 graph.

---

## 7. Reference: Josh Atticus Compressor

Same preset model: `resolutionShortSide`, `targetFps`, `sizeRatio`, `audioBitrate`. Their **Medium** preset is benchmarked around **~60%** reduction on a 200 MB 4K sample — not 85% ([Compressor README](https://github.com/JoshAtticus/Compressor)). Our **Best** is deliberately more aggressive than **High** (`sizeRatio 0.7` ≈ 30% savings only).

---

## 8. Proposed “Best” preset parameters (implemented in Phase 2)

| Field | Value |
|-------|--------|
| `resolutionShortSide` | `0` (original) |
| `targetFps` | `30` |
| `sizeRatio` | `0.15` |
| `videoCodec` | AV1 preferred; fallback H.265 → H.264 |
| `audioFormat` | AAC |
| `audioBitrate` | `160_000` bps |
| `qualitySlider` | `65` (high but not max, to avoid starving video bitrate) |
| Default tier | `PresetTier.BEST` |

---

## 9. Repo gap analysis (pre–Phase 2)

| Area | Was | Fixed in Phase 2 |
|------|-----|------------------|
| Presets | HIGH / MEDIUM / LOW; default MEDIUM | **BEST** added; default **BEST** |
| UI chips | High / Medium / Low | **Best** first |
| Size target | High `0.7` | Best **`0.15`** |
| AV1 min bitrate | Treated as H.264 | **AV1 factor** in `minVideoBitrateBps` |
| Mapper | Planned mime → only H264/H265 | **`codecFromMime(planned.videoMime)`** |
| `applyPreset` | No codec | **AV1 chain + AAC** for Best |
| 48 kHz | Not forced | Documented; passthrough OK for v1 |

---

## 10. Risks and test plan

| Risk | Mitigation |
|------|------------|
| No AV1 encoder (e.g. some Samsung A-series) | Fallback chain; test on **SM-E146B** |
| Target 0.15× impossible at min bitrate | Planner may downscale/fps/audio; user sees **target size warning** (`CompressFlowUiState.targetSizeWarning`) |
| Very short clips | Min bitrate dominates file size; effective ratio > 15% |

**Verification:** `VideoEncodePlannerTest`, `CompressSettingsMapperTest`, `./scripts/verify.sh`, manual encode on sample `VID20260901164336.mp4`.

---

## 11. References

1. [Android Media3 Transformer](https://developer.android.com/media/media3/transformer)
2. [androidx/media #1154 — Kush gauge / bitrate discussion](https://github.com/androidx/media/issues/1154)
3. [Josh Atticus Compressor](https://github.com/JoshAtticus/Compressor)
4. [ForaSoft — codec comparison (real content)](https://www.forasoft.com/learn/video-quality/articles-vqm/codec-comparison-real-content)
5. [Bitmovin — AV1 multi-codec dataset](https://bitmovin.com/blog/av1-multi-codec-dash-dataset/)
6. [ForaSoft — setting a VMAF quality target](https://www.forasoft.com/learn/video-quality/articles-vqm/setting-a-quality-target)
7. [Kush Gauge overview](https://blog.sporv.com/restoration-tips-kush-gauge/)

---

*Approved for implementation via project plan “Best preset: research doc first, then implementation” (user instructed full plan execution including Phase 2).*
