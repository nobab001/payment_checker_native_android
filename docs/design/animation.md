# Animation Guidelines

## Goals

Motion should confirm hierarchy and state — not entertain. Keep sequences under ~700ms for first paint of a screen.

## Timing

| Use | Duration | Easing |
|-----|----------|--------|
| Micro (press) | 100–150ms | spring / fast out |
| Fade in | 200–300ms | FastOutSlowIn |
| Slide + fade (cards) | 300–400ms | FastOutSlowIn |
| Stagger children | +100ms each | same |
| OTP cursor blink | 500ms reverse | Linear |

## Auth enter sequence (reference)

1. Logo scale + fade — 300ms  
2. Title/subtitle fade — 200ms  
3. Tag pill fade — 200ms  
4. Card slide-up + fade — 400ms  
5. Social icons stagger fade-up — 100ms apart  

## Press feedback

- Buttons/social: scale to ~0.92–0.97 with medium spring
- Prefer Material ripple on clickable surfaces

## Rules

- Prefer one-shot enter animations over infinite pulses.
- Cancel or skip heavy animation when `AccessibilityManager` reduces motion if you add global support later.
- No third-party Lottie/heavy animation packs unless product explicitly adopts them.
- Checkout web JS animations (separate from Compose) should stay subtle and must not block payment actions.

## Implementation notes

Use `androidx.compose.animation.*` and `animate*AsState`. Label all animations for debugging.
