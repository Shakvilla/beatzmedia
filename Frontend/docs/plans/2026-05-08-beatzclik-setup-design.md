# BeatzClik Music Streaming Platform Design Plan

## Overview
BeatzClik is a premium, fully responsive music streaming platform built with a feature-sliced architecture for maximum maintainability.

## Technology Stack
- **Framework**: React 19 (Vite + TypeScript)
- **Styling**: Tailwind CSS + MUI Base UI (Unstyled Primitives)
- **Routing**: TanStack Router (File-based, Type-safe)
- **State Management**: TanStack Query (Server State)
- **Performance**: TanStack Virtual (List Virtualization)
- **Data Grids**: TanStack Table
- **Forms**: TanStack Form
- **Icons**: Lucide React
- **Typography**: Plus Jakarta Sans, JetBrains Mono

## Design System (Extracted from Foundations)

### Color Palette
#### Dark Mode
- `bg`: #121212
- `surface`: #181818
- `surface-2`: #282828
- `surface-3`: #2A2A2A
- `green`: #1ED760
- `gold`: #E8B84B
- `red`: #E22134
- `blue`: #2E77F0

#### Light Mode
- `bg`: #FAF7F2
- `surface`: #FFFFFF
- `surface-2`: #F0EBE2
- `surface-3`: #E8E2D8
- `green`: #1A9E48
- `gold`: #E8B84B
- `red`: #C8192A
- `blue`: #2563D4

### Typography
- **Display**: 36px / 800
- **Title**: 24px / 800
- **Body Strong**: 14px / 700
- **Body**: 14px / 400
- **Mono**: 11px (JetBrains Mono)

## Folder Structure (Feature-Based)
```text
src/
 ├── assets/
 ├── components/
 │    └── ui/ (MUI Base UI wrappers + Tailwind)
 ├── features/
 │    ├── auth/
 │    ├── player/
 │    ├── discover/
 │    ├── library/
 │    └── search/
 ├── hooks/
 ├── lib/ (Axios, TanStack Config)
 ├── routes/ (TanStack Router Tree)
 ├── types/
 └── utils/
```

## Implementation Phases
1. **Phase 1**: Project Scaffolding & Design Token integration.
2. **Phase 2**: TanStack Foundation (Router & Query setup).
3. **Phase 3**: UI Component Library (Base UI integration).
4. **Phase 4**: Feature Implementation (Player, Discover, etc.) - UI provided by USER.
