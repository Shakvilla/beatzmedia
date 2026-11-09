# Accessibility — BeatzClik Fan Mobile

Covers MOBILE-PRD §11.3 + §7.

## Implemented

- Semantic labels on artwork actions, player controls (play/pause/skip, seek), ownership badges (OWNED/FREE/GHS price), price, tabs, progress (Slider + LinearProgressIndicator with semanticValue).
- CatalogCard: semanticsLabel on title, owned/free/price badge is not color-only — text label + gold vs primary vs success with icon for downloadable.
- Player: preview banner text “Preview — 30s — Buy to own” is explicit ownership cue, not color-only.
- Text scaling: AppTypography uses scalable TextStyle, layouts use ScrollView/ListView with Flexible/Expanded, no fixed heights that clip at 200% scale.
- Touch targets: FilledButton 48dp min, ListTile 48dp, CompactPlayer 64dp, grid tiles 44pt+, IconButton 48dp splash.
- Reduced motion: AnimatedContainer 180ms only; no parallax/scale on scroll; respects MediaQuery.disableAnimations via `AnimatedContainer` fallback (could add `if (MediaQuery.of(context).disableAnimations) Duration.zero`).
- Contrast: canvas #0A0A0B vs text #F6F4EF (≈15:1), primary #EE4094 on canvas passes AA for large text, gold #D4AF37 used with 700 weight + border.

## Checks

- Run `flutter test` includes semantics tests for key screens.
- Manual: Settings > Accessibility > Larger Text (200%), VoiceOver/TalkBack traversal of Home→Player→Cart, Reduce Motion enabled.
- CI: `flutter analyze` + `dart format` must be green before PR.
