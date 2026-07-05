# Layout Audit Report

**Date:** 2026-06-13
**Scope:** All keyboard layout XML variants

---

## 1. Layout Files Found

| # | File | Lines | Purpose |
|---|------|-------|---------|
| 1 | `app/src/main/res/layout/main_layout.xml` | 166 | Default / Portrait keyboard |
| 2 | `app/src/main/res/layout-land/main_layout.xml` | 164 | Landscape keyboard |
| 3 | `app/src/main/res/layout-sw600dp/main_layout.xml` | 167 | Tablet (sw600dp) keyboard |
| 4 | `app/src/main/res/layout/floating_keyboard_layout.xml` | 156 | Floating keyboard window |
| 5 | `app/src/main/res/layout/item_shortcut.xml` | 16 | Shortcut toolbar item |
| 6 | `app/src/main/res/layout/layout_floating_dock.xml` | 41 | Floating mode dock bar |

---

## 2. View ID Comparison Across main_layout Variants

All three main_layout variants (portrait, landscape, sw600dp) define the **same 16 View IDs**. No missing or extra IDs between them.

| View ID | Type | Portrait | Landscape | sw600dp |
|---------|------|:--------:|:---------:|:-------:|
| `keyboard_background_video` | PlayerView | ✅ | ✅ | ✅ |
| `keyboard_background_image` | ImageView | ✅ | ✅ | ✅ |
| `suggestionView_parent` | ConstraintLayout | ✅ | ✅ | ✅ |
| `suggestion_progressbar` | ProgressBar | ✅ | ✅ | ✅ |
| `toolbar_toggle_button` | ShapeableImageView | ✅ | ✅ | ✅ |
| `upper_area_content_container` | FrameLayout | ✅ | ✅ | ✅ |
| `suggestion_recycler_view` | RecyclerView | ✅ | ✅ | ✅ |
| `shortcut_toolbar_recyclerview` | RecyclerView | ✅ | ✅ | ✅ |
| `suggestion_visibility` | ShapeableImageView | ✅ | ✅ | ✅ |
| `candidate_tab_layout` | TabLayout | ✅ | ✅ | ✅ |
| `keyboard_view` | TenKey | ✅ | ✅ | ✅ |
| `tablet_view` | TabletKeyboardView | ✅ | ✅ | ✅ |
| `qwerty_view` | QWERTYKeyboardView | ✅ | ✅ | ✅ |
| `custom_layout_default` | FlickKeyboardView | ✅ | ✅ | ✅ |
| `candidates_row_view` | RecyclerView | ✅ | ✅ | ✅ |
| `keyboard_symbol_view` | CustomSymbolKeyboardView | ✅ | ✅ | ✅ |

**Verdict:** IDs are consistent across main_layout variants. ViewBinding will generate the same `MainLayoutBinding` class regardless of device configuration.

---

## 3. Views Missing in Some Variants

### 3.1 Views absent from `floating_keyboard_layout.xml`

The floating keyboard is a separate layout with its own ViewBinding class (`FloatingKeyboardLayoutBinding`). It intentionally omits several views present in `main_layout.xml`:

| View | main_layout | floating | Impact |
|------|:-----------:|:--------:|--------|
| `candidate_tab_layout` (TabLayout) | ✅ | ❌ | Candidate tab switching unavailable in floating mode |
| `suggestion_progressbar` (ProgressBar) | ✅ | ❌ | No loading indicator in floating mode |
| `toolbar_toggle_button` (ShapeableImageView) | ✅ | ❌ | No toolbar toggle in floating mode |
| `upper_area_content_container` (FrameLayout) | ✅ | ❌ | No shortcut toolbar in floating mode |
| `shortcut_toolbar_recyclerview` (RecyclerView) | ✅ | ❌ | No shortcut toolbar in floating mode |
| `tablet_view` (TabletKeyboardView) | ✅ | ❌ | No tablet keyboard in floating mode |
| `keyboard_background_video` (PlayerView) | ✅ | ❌ (has `floating_keyboard_background_video`) | Different ID prefix |
| `keyboard_background_image` (ImageView) | ✅ | ❌ (has `floating_keyboard_background_image`) | Different ID prefix |

**CRASH RISK:** Code referencing `floatingKeyboardLayoutBinding.candidateTabLayout` or `floatingKeyboardLayoutBinding.toolbarToggleButton` will fail at compile time (ViewBinding). However, any runtime `findViewById` on the wrong layout will return null and crash if not null-checked.

### 3.2 Views present in floating but absent from main_layout

| View ID | Type | Purpose |
|---------|------|---------|
| `floating_keyboard_background_container` | FrameLayout | Rounded clip container for background |
| `floating_keyboard_content` | ConstraintLayout | Root content wrapper |
| `floating_keyboard_container` | FrameLayout | Keyboard view container |
| `keyboard_view_floating` | TenKey | Floating-specific TenKey (different ID!) |
| `qwerty_view_floating` | QWERTYKeyboardView | Floating-specific QWERTY (different ID!) |
| `custom_layout_floating` | FlickKeyboardView | Floating-specific custom (different ID!) |
| `floating_symbol_keyboard` | CustomSymbolKeyboardView | Floating-specific symbol (different ID!) |
| `drag_handle` | ImageView | Drag handle for repositioning |
| `floating_hide_keyboard_btn` | ImageView | Hide keyboard button |

**CRASH RISK:** The floating layout uses `_floating` suffixed IDs for keyboard views. Code that accesses `mainView.keyboardView` will NOT work on the floating binding. IMEService correctly uses separate bindings (`mainLayoutBinding` vs `floatingKeyboardLayoutBinding`), so this is handled.

---

## 4. Height / Dimension Reference Issues

### 4.1 Dimension definitions (from `core/src/main/res/values/dimen.xml`)

| Dimen Name | Value | Used In |
|------------|-------|---------|
| `keyboard_height` | 280dp | portrait, sw600dp keyboard views |
| `keyboard_height_land` | 200dp | landscape TenKey + candidates |

### 4.2 Height assignments per variant

| View | Portrait | Landscape | sw600dp |
|------|----------|-----------|---------|
| `keyboard_view` (TenKey) | `@dimen/keyboard_height` (280dp) | `@dimen/keyboard_height_land` (200dp) | `@dimen/keyboard_height` (280dp) |
| `tablet_view` | `@dimen/keyboard_height` (280dp) | `@dimen/keyboard_height` (**280dp**) | `@dimen/keyboard_height` (280dp) |
| `qwerty_view` | `@dimen/keyboard_height` (280dp) | `@dimen/keyboard_height` (**280dp**) | `@dimen/keyboard_height` (280dp) |
| `custom_layout_default` | `@dimen/keyboard_height` (280dp) | `@dimen/keyboard_height` (**280dp**) | `@dimen/keyboard_height` (280dp) |
| `candidates_row_view` | `@dimen/keyboard_height` (280dp) | `@dimen/keyboard_height_land` (200dp) | `@dimen/keyboard_height` (280dp) |

**ISSUE: Landscape height inconsistency.**
In landscape, `keyboard_view` (TenKey) and `candidates_row_view` use `keyboard_height_land` (200dp), but `tablet_view`, `qwerty_view`, and `custom_layout_default` still use `keyboard_height` (280dp). This means the tablet/QWERTY/custom views are **80dp taller** than the TenKey in landscape. If the code calculates keyboard area based on TenKey height, the taller views will overflow or overlap the suggestion area.

**Recommendation:** Verify that the landscape `tablet_view`, `qwerty_view`, and `custom_layout_default` are intentionally taller. If not, they should use `@dimen/keyboard_height_land`.

### 4.3 `tablet_view` extra padding in sw600dp

`layout-sw600dp/main_layout.xml` line 131:
```xml
android:paddingBottom="4dp"
```
This attribute exists **only** in the sw600dp variant. No other `tablet_view` declaration has it. This may cause slight vertical alignment differences.

### 4.4 Floating keyboard hardcoded dimensions

The floating layout uses hardcoded dp values instead of `@dimen/` references:

| View | Attribute | Value | Notes |
|------|-----------|-------|-------|
| `floating_keyboard_background_container` | `layout_height` | `306dp` | Should be `@dimen/` |
| `suggestion_recycler_view` | `layout_height` | `58dp` | Different from main_layout's `match_parent` |
| `suggestion_visibility` | width/height | `42dp` | **Different from `40dp` in main layouts** |
| `floating_keyboard_container` | `layout_height` | `200dp` | Should be `@dimen/` |
| `floating_symbol_keyboard` | `layout_height` | `320dp` | Should be `@dimen/` |
| `candidates_row_view` | `layout_height` | `200dp` | Should be `@dimen/` |
| `drag_handle` | `layout_width` | `200dp` | Should be `@dimen/` |

### 4.5 `suggestionView_parent` height

| Variant | Height |
|---------|--------|
| portrait | `40dp` |
| landscape | `40dp` |
| sw600dp | `40dp` |
| floating | `wrap_content` |

The floating variant uses `wrap_content`, which is correct for its dynamic layout, but means the suggestion bar height differs from the main keyboard.

### 4.6 `candidate_tab_layout` height

All main_layout variants: `36dp`. Consistent. Not present in floating.

---

## 5. Default Visibility Inconsistencies

| View | Portrait | Landscape | sw600dp | Floating |
|------|----------|-----------|---------|----------|
| `keyboard_view` (TenKey) | `gone` | **`visible`** | `gone` | N/A (`keyboard_view_floating`: no visibility attr, defaults to `visible`) |
| `tablet_view` | `gone` | `gone` | **`visible`** | N/A (absent) |
| `qwerty_view` | `gone` | `gone` | `gone` | `gone` |
| `custom_layout_default` | `gone` | `gone` | `gone` | `gone` |
| `candidates_row_view` | `gone` | `gone` | `gone` | `gone` |
| `keyboard_symbol_view` | `gone` | `gone` | `gone` | N/A (`floating_symbol_keyboard`: `gone`) |

**Note:** The landscape variant defaults to showing TenKey (`keyboard_view` visible), while sw600dp defaults to showing tablet (`tablet_view` visible). This appears intentional but means the IME service must handle all three initial states correctly.

---

## 6. Accessibility Issues

### 6.1 Missing `importantForAccessibility`

| View | Portrait | Landscape | sw600dp |
|------|:--------:|:---------:|:-------:|
| `keyboard_view` (TenKey) | `yes` | **MISSING** | `yes` |
| `tablet_view` | `yes` | `yes` | `yes` |
| `qwerty_view` | `yes` | `yes` | `yes` |
| `custom_layout_default` | `yes` | `yes` | `yes` |

**ISSUE:** `keyboard_view` in `layout-land/main_layout.xml` is missing `android:importantForAccessibility="yes"`. While the default behavior for a custom view is determined by the view class, explicitly setting it ensures consistent TalkBack behavior across all configurations.

### 6.2 Missing `contentDescription` on interactive views

The following interactive views lack `contentDescription` in **all** main_layout variants:

| View | Has contentDescription | Impact |
|------|:---------------------:|--------|
| `toolbar_toggle_button` | ❌ | Screen reader says nothing about this button's purpose |
| `suggestion_visibility` | ❌ | Screen reader says nothing about the expand/collapse toggle |
| `keyboard_view` (TenKey) | ❌ | May be announced as generic view |
| `tablet_view` | ❌ | May be announced as generic view |
| `qwerty_view` | ❌ | May be announced as generic view |
| `custom_layout_default` | ❌ | May be announced as generic view |
| `candidate_tab_layout` | ❌ | Tab labels provide some context, but the container itself has none |
| `keyboard_symbol_view` | ❌ | No context for screen reader |
| `candidates_row_view` | ❌ | No context for screen reader |

### 6.3 Floating keyboard accessibility

| View | contentDescription | Issue |
|------|-------------------|-------|
| `drag_handle` | `"Drag to move keyboard"` | **Hardcoded English** — not localized (should use `@string/` reference) |
| `floating_hide_keyboard_btn` | **NONE** | Interactive button with no description |
| `floating_keyboard_background_image` | `@null` | Acceptable — decorative |
| `keyboard_view_floating` | **NONE** | Interactive view, no description |
| `qwerty_view_floating` | **NONE** | Interactive view, no description |
| `custom_layout_floating` | **NONE** | Interactive view, no description |
| `floating_symbol_keyboard` | **NONE** | Interactive view, no description |

### 6.4 `item_shortcut.xml` accessibility

```xml
<FrameLayout ...>  <!-- no accessibility attributes -->
    <ImageView
        android:id="@+id/item_image"
        android:importantForAccessibility="no"
        ... />  <!-- no contentDescription -->
</FrameLayout>
```

The `ImageView` is explicitly marked `importantForAccessibility="no"`, and the parent `FrameLayout` has no accessibility attributes. **Screen readers will completely skip shortcut items.** If shortcuts have meaningful icons (menu, paste, etc.), they should have `contentDescription` using `@string/` references.

### 6.5 `layout_floating_dock.xml` accessibility

```xml
<ImageView
    android:id="@+id/dock_icon"
    ...
    tools:ignore="ContentDescription" />  <!-- explicitly suppressing lint warning -->
```

The dock icon (keyboard switcher) has **no contentDescription** and explicitly ignores the lint warning. This means the floating dock is not accessible to screen readers.

### 6.6 `keyboard_background_image` in main layouts

All three main_layout variants set `android:contentDescription="@null"` on `keyboard_background_image`. This is correct — it's a decorative background and should be invisible to screen readers.

---

## 7. Summary of Critical Issues

### Potential Crash Risks

1. **Landscape height overflow:** In landscape mode, `tablet_view`, `qwerty_view`, and `custom_layout_default` use `keyboard_height` (280dp) while the overall keyboard area expects `keyboard_height_land` (200dp). If the IME window height is calculated based on TenKey height, these views may overflow and cause layout clipping or touch event issues.

2. **Floating layout ID mismatch:** The floating layout uses different IDs (`keyboard_view_floating` vs `keyboard_view`). Any code path that accidentally uses the wrong binding will get a null reference. The IMEService currently handles this correctly with separate bindings.

### Accessibility Defects (6 items)

1. `keyboard_view` missing `importantForAccessibility` in landscape variant
2. `toolbar_toggle_button` has no `contentDescription` in any variant
3. `suggestion_visibility` has no `contentDescription` in any variant
4. `drag_handle` in floating layout has hardcoded English `contentDescription`
5. `floating_hide_keyboard_btn` has no `contentDescription`
6. `item_shortcut.xml` items are completely invisible to screen readers
7. `layout_floating_dock.xml` dock icon has no `contentDescription`

### Dimension Inconsistencies (4 items)

1. Landscape `tablet_view`/`qwerty_view`/`custom_layout_default` use `keyboard_height` (280dp) while `keyboard_view` uses `keyboard_height_land` (200dp)
2. `suggestion_visibility` is `42dp` in floating vs `40dp` in main layouts
3. `tablet_view` in sw600dp has unique `paddingBottom="4dp"`
4. Floating keyboard uses 7 hardcoded dp values instead of `@dimen/` references

### Visibility Defaults (intentional but worth noting)

1. Landscape defaults to TenKey visible
2. sw600dp defaults to tablet visible
3. Portrait defaults to nothing visible (all gone)
