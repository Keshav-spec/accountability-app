---
name: Mindful Wabi-Sabi Sanctuary
colors:
  surface: '#faf9f5'
  surface-dim: '#dbdad6'
  surface-bright: '#faf9f5'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f4f4f0'
  surface-container: '#efeeea'
  surface-container-high: '#e9e8e4'
  surface-container-highest: '#e3e2df'
  on-surface: '#1b1c1a'
  on-surface-variant: '#414845'
  inverse-surface: '#2f312e'
  inverse-on-surface: '#f2f1ed'
  outline: '#727974'
  outline-variant: '#c1c8c3'
  surface-tint: '#466558'
  primary: '#0d2c22'
  on-primary: '#ffffff'
  primary-container: '#244237'
  on-primary-container: '#8daea0'
  inverse-primary: '#adcebf'
  secondary: '#43664d'
  on-secondary: '#ffffff'
  secondary-container: '#c2e9c9'
  on-secondary-container: '#486a51'
  tertiary: '#4c1200'
  on-tertiary: '#ffffff'
  tertiary-container: '#6c250b'
  on-tertiary-container: '#f28b6a'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#c8eada'
  primary-fixed-dim: '#adcebf'
  on-primary-fixed: '#012017'
  on-primary-fixed-variant: '#2f4d41'
  secondary-fixed: '#c5eccc'
  secondary-fixed-dim: '#aad0b1'
  on-secondary-fixed: '#00210e'
  on-secondary-fixed-variant: '#2c4e36'
  tertiary-fixed: '#ffdbd0'
  tertiary-fixed-dim: '#ffb59e'
  on-tertiary-fixed: '#390b00'
  on-tertiary-fixed-variant: '#7a2f15'
  background: '#faf9f5'
  on-background: '#1b1c1a'
  surface-variant: '#e3e2df'
typography:
  headline-xl:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-xl-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.015em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  subheading:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
  label-caps:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.08em
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-desktop: 1.5rem
  margin: 1.25rem
  margin-desktop: 2.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

The design system embodies a harmonious blend of Japanese wabi-sabi intentionality and Scandinavian warmth (Japandi minimalism). It rejects the hyper-stimulated, anxiety-inducing paradigms of conventional productivity tools. Instead, it positions task management and intention-setting as restorative daily rituals.

The target audience encompasses mindful professionals, neurodivergent thinkers, and wellness-focused individuals seeking calm self-direction without guilt or algorithmic pressure. The visual ethos communicates unhurried clarity: generous negative space, gentle transitions, tactile natural materials translated into digital space, and reassuring typographic poise. Rather than urging users to optimize every second, the interface offers an organic refuge—giving every action room to breathe.

## Colors

The palette draws directly from raw earthenware, weathered stone, and deep pine forests. 

- **Primary Forest Sage (`#244237`, deep tone `#1E372E`):** Represents grounding stability and anchor points. Used for primary interactive actions, high-emphasis icons, and definitive headings.
- **Secondary Eucalyptus (`#84A98C`, active midtone `#44705D`):** Serves as a soothing bridge for soft states, subtle progress indicators, selected outlines, and badge backgrounds.
- **Tertiary Terracotta & Amber (`#D97757`, warm highlight `#E9C46A`):** Applied sparingly to draw warmth to gentle nudges, streak milestones, or time-sensitive reminders without triggering alarms.
- **Neutral Stone & Linen (`#FAF9F5`, secondary surface `#F4F2EC`, border stone `#E6E3DA`):** Replaces clinical cool whites with comforting, low-glare organic parchment tones.

Text hierarchies leverage soft charcoal (`#2D322E`) for primary legibility and muted pine-slate (`#6E7771`) for secondary guidance.

## Typography

Typography balances structural clarity with gentle curvature. Headings utilize tight tracking and measured line-heights to deliver confidence without aggressive weight.

Section markers and navigational metadata employ `label-caps` in uppercase with wide tracking (`0.08em`), establishing visual rhythm while maintaining legibility. Body text prioritizes spacious line heights (`1.6x`) to mimic curated editorial publications, making dense task lists and introspective journaling feel light and approachable.

## Layout & Spacing

The layout model emphasizes expansive breathing room. Interfaces follow a column grid (4 columns on mobile, 8 on tablet, 12 on desktop max-width 720px for handheld/focused reading applications).

Vertical rhythm leans on asymmetrical balance. Content containers stack with comfortable gaps (`space-lg` to `space-xl`), intentionally discouraging information overcrowding. Screens preserve generous top margins (`space-xl`) between titles and content clusters, reminding the user that empty space is an asset, not a vacancy to fill.

## Elevation & Depth

Visual hierarchy is constructed through tonal layering and delicate glassmorphism rather than artificial high-contrast drop shadows.

- **Base Canvas:** Linen stone (`#FAF9F5`).
- **Surface Elevation 1 (Cards, Modals):** Off-white parchment (`#FFFFFF` or `#FDFCF9`) paired with a translucent, hairline border (`1px solid #E6E3DA`).
- **Surface Elevation 2 (Floating Action Buttons, Menus):** Deep forest sage (`#244237`) or frosted linen (`rgba(250, 249, 245, 0.85)` with `backdrop-filter: blur(16px)`).
- **Shadow Signature:** Shadows are warm-tinted and deeply diffused: `0 8px 24px -4px rgba(36, 66, 55, 0.08), 0 2px 6px -1px rgba(36, 66, 55, 0.04)`.
- **Active / Pressed Elevation:** Elements scale down slightly (`scale(0.98)`) and decrease shadow diffusion to suggest soft, physical depression into natural paper.

## Shapes

Shapes are soft and welcoming, emulating smooth river stones and organic ceramics.

Standard structural containers (cards, dialogue sheets, input fields) employ a base curvature of `1rem` to `1.5rem` (`rounded-lg` / `rounded-xl`). Interactive touch elements—including bottom tab navigation indicators, filter chips, action pills, and status tags—adopt complete pill geometry (`rounded-full` / 9999px) to communicate gentleness and seamless tactile flow.

## Components

### Buttons
- **Primary FAB:** Sized at 56×56px or 64×64px, pill/squircle shape, colored Deep Forest Sage (`#244237`) with a warm stone icon (`#FAF9F5`). Casts an ambient sage shadow.
- **Secondary & Tertiary Buttons:** Ghost or subtle stone pill containers (`#F4F2EC`) with muted borders (`#E6E3DA`) and deep sage typography (`#244237`). Hover/focus transitions shift background to soft eucalyptus tint (`rgba(132, 169, 140, 0.15)`).

### Input Fields & Checkboxes
- **Input Fields:** Generous touch targets (48px height minimum). Crisp 1px border in pale stone (`#E6E3DA`), resting on pure white or subtle warm canvas. Labels sit comfortably above or integrated in refined floating positions. Active focus transitions to a soft eucalyptus ring (`#84A98C`) without harsh neon rings.
- **Checkboxes & Check-in Radios:** Softly rounded squares (`6px` border radius) or circular nodes. Inactive states feature hairline stone strokes; checked states transition smoothly to deep sage (`#244237`) with warm white check glyphs.

### Chips & Segmented Tabs
- **Navigation Tabs:** Horizontal pill rail. The active state highlights via an organic bar or filled capsule (`#244237` or soft eucalyptus tint `#E4ECE7`) rather than plain underlined text, eliminating visual clutter.
- **Filter & Mood Chips:** Compact pills with `space-sm` padding, displaying subtle status indicators and optional terracotta accent pips for urgent flags.

### Cards & Sheets
- **Task & Reflection Cards:** Framed in low-contrast outlines (`1px solid #E6E3DA`) over warm stone backgrounds. Internal padding is generous (`space-lg`), giving headlines and body copy clear separation.
- **Bottom Drawers & Modals:** Generous 28px top-corner radiuses, soft top drag handles (`36×4px` rounded bar in `#D5D2C7`), and high-blur frosted backgrounds.