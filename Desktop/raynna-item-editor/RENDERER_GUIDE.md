# Cache Editor Renderer Guide

This document explains the current item preview renderer in `cache-editor` as it exists now.

The goal of this renderer is:

- match the client item rotation / zoom / offset behavior as closely as possible
- render inventory items into a RuneScape-style slot basis
- keep the editor preview readable at a larger on-screen size

Primary implementation files:

- [ItemModelRenderer.java](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java)
- [Cs2ScriptEditorApp.java](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\Cs2ScriptEditorApp.java)

## 1. High-Level Flow

The inventory preview pipeline is:

1. Load the item definition.
2. Load and decode the model.
3. Apply item model scaling.
4. Build the inventory transform from item rotation, zoom, and offsets.
5. Transform all vertices into view space.
6. Project transformed vertices into 2D screen coordinates.
7. Backface-cull hidden faces.
8. Shade visible faces.
9. Render into an internal high-resolution image.
10. Resolve that image into the final preview image shown in the editor.

For inventory mode, the editor requests a `108x96` image, which is a 3x visual version of a `36x32` RuneScape inventory slot.

Relevant UI code:

- [Cs2ScriptEditorApp.java#L1956](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\Cs2ScriptEditorApp.java#L1956)
- [Cs2ScriptEditorApp.java#L1977](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\Cs2ScriptEditorApp.java#L1977)
- [Cs2ScriptEditorApp.java#L2057](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\Cs2ScriptEditorApp.java#L2057)
- [Cs2ScriptEditorApp.java#L2095](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\Cs2ScriptEditorApp.java#L2095)

## 2. Item Definition Field Mapping

The inventory item definition fields map like this:

- `rotation1` = X tilt
- `rotation2` = Y rotation
- `rotation3` = Z rotation
- `offset1` = X offset
- `offset2` = Y offset
- `zoom` = item zoom value
- `scaleX`, `scaleY`, `scaleZ` = model scale values

In the current code those are read from `ItemDefinitionRecord` and used in:

- [ItemModelRenderer.java#L90](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L90)

## 3. Model Scaling

Before any rotation or projection, model vertices are scaled.

Implementation:

- [ItemModelRenderer.java#L121](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L121)
- [ItemModelRenderer.java#L378](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L378)

Formula per vertex:

```text
x' = x * scaleX / 128
y' = y * scaleY / 128
z' = z * scaleZ / 128
```

Meaning:

- `128` means unchanged size
- values above `128` enlarge that axis
- values below `128` shrink that axis

## 4. Client Angle Conversion

The client stores item angles in a `0..2047` circle.

Implementation:

- [ItemModelRenderer.java#L525](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L525)

Formula:

```text
degrees = (value & 2047) * (360.0 / 2048.0)
radians = degrees * pi / 180
```

Examples:

- `0` = `0 deg`
- `512` = `90 deg`
- `1024` = `180 deg`
- `1536` = `270 deg`

## 5. Inventory Transform Inputs

For inventory mode, the renderer builds effective values from the stored item values and the current preview deltas.

Formula:

```text
effectiveZ = item.rotation3 + previewDeltaZ
effectiveY = item.rotation2 + previewDeltaY
effectiveX = item.rotation1 + previewDeltaX
```

Then:

```text
xRadians = toRadians(clientAngleToDegrees(effectiveX))
zoomUnits = zoom * 4.0
translateX = (offset1 + previewOffsetX) * 4.0
translateY = sin(xRadians) * zoomUnits - YA()/2 + (offset2 + previewOffsetY) * 4.0
translateZ = cos(xRadians) * zoomUnits + (offset2 + previewOffsetY) * 4.0
```

In the current implementation, `YA()` is matched by using the model minimum Y bound.

Current code:

- [ItemModelRenderer.java#L127](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L127)

Equivalent code expression:

```text
modelMinYHalf = modelMinY / 2.0
translateY = sin(xRadians) * zoomUnits - modelMinYHalf + offsetYTerm
```

Important detail:

- in the client, `YA()` is the model minimum Y bound, not a positive height
- that sign matters
- this was one of the critical fixes for matching vertical placement

## 6. Inventory Transform Order

The current inventory transform follows the client item-sprite order:

1. Rotate around Z by `-rotation3`
2. Rotate around Y by `rotation2`
3. Translate using `zoom`, `offset1`, `offset2`, and `YA()/2`
4. Rotate around X by `rotation1`

Current code:

- [ItemModelRenderer.java#L142](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L142)
- [ItemModelRenderer.java#L394](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L394)

Written mathematically:

```text
Pfinal = Rx(rotation1) * ( T + Ry(rotation2) * Rz(-rotation3) * P )
```

Where:

- `P` = original scaled 3D vertex
- `Rz` = Z-axis rotation matrix
- `Ry` = Y-axis rotation matrix
- `Rx` = X-axis rotation matrix
- `T` = translation vector `(translateX, translateY, translateZ)`

## 7. Axis Rotation Matrices

The renderer uses axis-angle matrix construction internally through `ViewTransform`.

The standard axis rotation versions are:

### Z rotation by angle `z`

```text
[ cos(z)  -sin(z)   0 ]
[ sin(z)   cos(z)   0 ]
[   0        0      1 ]
```

### Y rotation by angle `y`

```text
[  cos(y)   0   sin(y) ]
[    0      1     0    ]
[ -sin(y)   0   cos(y) ]
```

### X rotation by angle `x`

```text
[ 1    0        0     ]
[ 0  cos(x)  -sin(x)  ]
[ 0  sin(x)   cos(x)  ]
```

The renderer composes these into one transform matrix, then applies:

```text
x' = m00*x + m10*y + m20*z + tx
y' = m01*x + m11*y + m21*z + ty
z' = m02*x + m12*y + m22*z + tz
```

Implementation:

- [ItemModelRenderer.java#L394](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L394)

## 8. Projection

After the 3D transform, vertices are projected into 2D.

Implementation:

- [ItemModelRenderer.java#L296](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L296)

### Logical Inventory Slot Basis

The RuneScape inventory slot basis is:

- width = `36`
- height = `32`
- center X = `16`
- center Y = `16`
- focal length = `512`

Current constants:

```text
INVENTORY_SPRITE_WIDTH = 36
INVENTORY_SPRITE_HEIGHT = 32
INVENTORY_SPRITE_CENTER_X = 16
INVENTORY_SPRITE_CENTER_Y = 16
INVENTORY_FOCAL_LENGTH = 512
```

### Scaled Preview Basis

The editor preview renders larger than the original slot, so the renderer scales the slot basis to the requested output size:

```text
inventoryScaleX = width / 36.0
inventoryScaleY = height / 32.0
spriteCenterX = 16 * inventoryScaleX
spriteCenterY = 16 * inventoryScaleY
focalLength = 512 * average(inventoryScaleX, inventoryScaleY)
```

### Per-Vertex Projection Formula

For each transformed vertex:

```text
depthValue = z
if abs(depthValue) < 50:
    depthValue = sign(z) * 50

screenX = spriteCenterX + (x * focalLength) / depthValue
screenY = spriteCenterY + (y * focalLength) / depthValue
depth   = -depthValue
```

The minimum depth clamp prevents projection blowups when a vertex gets too close to the projection plane.

## 9. Backface Culling

Faces that point away from the viewer are skipped.

Current code:

- main face loop in [ItemModelRenderer.java#L90](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L90)

Formula for triangle `(a, b, c)` using projected coordinates:

```text
cross =
    (xb - xa) * (yc - ya)
  - (yb - ya) * (xc - xa)

if cross >= 0:
    skip face
```

Only faces with the desired winding survive.

## 10. Depth Sorting

The renderer sorts visible faces by average projected depth.

Formula:

```text
faceDepth = (depth[a] + depth[b] + depth[c]) / 3.0
```

Then faces are sorted before drawing.

This is a painter-style face ordering rather than a z-buffer.

## 11. Lighting

Face lighting is computed from the face normal and a fixed light vector.

Implementation:

- [ItemModelRenderer.java#L444](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L444)

For a triangle `(a, b, c)`:

```text
AB = B - A
AC = C - A
N = normalize(AB x AC)
```

The light direction is:

```text
L = normalize(-0.45, 0.7, -0.55)
```

Brightness formula:

```text
light = clamp(0.55 + dot(N, L), 0.28, 1.0)
```

Meaning:

- `dot(N, L)` controls whether the face points toward the light
- `0.55` is the base ambient term
- final brightness never drops below `0.28`
- final brightness never exceeds `1.0`

## 12. Color Conversion

Model face colors are stored as packed HSL-style values.

Implementation:

- [ItemModelRenderer.java#L481](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L481)
- [ItemModelRenderer.java#L490](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L490)
- [ItemModelRenderer.java#L471](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L471)

Packed value unpack:

```text
hue   = ((packed >> 10) & 0x3F) / 64.0
sat   = ((packed >> 7)  & 0x07) / 8.0
light = ( packed        & 0x7F) / 128.0
```

Current adjustments:

```text
sat   = min(1.0, sat * 1.25 + 0.08)
light = clamp(light * 1.15, 0.0, 1.0)
```

Then HSL is converted to RGB.

After that, per-channel lighting is applied:

```text
r = clamp(r * light)
g = clamp(g * light)
b = clamp(b * light)
```

## 13. Internal Render Resolution

The preview uses internal oversampling to avoid a low-quality raw image.

Current constant:

```text
INTERNAL_RENDER_SCALE = 3
```

For a requested output of `108x96`, the raw render target becomes:

```text
rawWidth = 108 * 3 = 324
rawHeight = 96 * 3 = 288
```

This helps preserve edge detail and improves the final preview quality.

## 14. Downsample Step

After the raw image is drawn, it is resolved to the final requested size.

Implementation:

- [ItemModelRenderer.java#L332](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\itemeditor\render\ItemModelRenderer.java#L332)

The current downsample uses:

- bilinear interpolation
- quality render hints

So the final preview image is smoother and more readable than directly rendering at the tiny logical slot size.

## 15. Inventory Preview Sizing In The UI

In the editor UI, the inventory preview box is:

- width = `108`
- height = `96`

Relevant code:

- [Cs2ScriptEditorApp.java#L1956](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\Cs2ScriptEditorApp.java#L1956)
- [Cs2ScriptEditorApp.java#L2057](C:\Users\andre\Desktop\727-source\cache-editor\src\main\java\raynna\tools\Cs2ScriptEditorApp.java#L2057)

This means:

- the preview visually represents a 3x `36x32` slot
- the renderer is asked to render directly at that preview size
- the UI no longer performs an extra stretch after render

That was an important quality fix.

## 16. Worked Example

Take an inventory item with:

```text
zoom = 1144
rotation1 = 2024
rotation2 = 1507
rotation3 = 0
offset1 = 1
offset2 = 0
scaleX = 128
scaleY = 128
scaleZ = 128
```

### Angle Conversion

```text
xDeg = 2024 * 360 / 2048 = 355.78125
yDeg = 1507 * 360 / 2048 = 264.90234
zDeg = 0
```

### Zoom and Translation

```text
zoomUnits = 1144 * 4 = 4576
translateX = 1 * 4 = 4
translateY = sin(355.78125 deg) * 4576 - YA()/2 + 0
translateZ = cos(355.78125 deg) * 4576 + 0
```

Since:

```text
sin(355.78125 deg) ~= -0.07356
cos(355.78125 deg) ~=  0.99729
```

Then:

```text
translateY ~= -336.6 - YA()/2
translateZ ~= 4563.6
```

The final transformed vertex path is:

```text
Pfinal = Rx(355.78125) * ( T + Ry(264.90234) * Rz(0) * P )
```

Then projection converts that 3D point into 2D slot coordinates.

## 17. Important Notes

### Zoom Is Not Just Scale

In this renderer, matching the client means `zoom` affects the translation terms:

```text
translateY = sin(x) * zoomUnits + ...
translateZ = cos(x) * zoomUnits + ...
```

So in the client-style item path, zoom is part of camera placement behavior, not only a post-scale factor.

### Vertical Placement Depends On `YA()`

One of the most important correctness details is:

```text
-YA()/2
```

That term depends on the model minimum Y bound, not a positive model height.

If that sign is wrong, some items appear too high or too low in the slot.

### Inventory Mode Is Special

Inventory mode is not the same as the worn preview path.

Inventory mode:

- uses the client item-sprite style transform
- uses slot-based projection
- uses the item definition zoom/rotation/offset fields

Worn mode:

- uses a simpler preview path
- does not use the same inventory slot formulas

## 18. Summary

The current working inventory renderer can be summarized as:

```text
1. Decode model
2. Apply item scale
3. Convert client angles to degrees/radians
4. Build client-style item transform:
   Z rotate
   Y rotate
   translate using zoom + offsets + YA()/2
   X rotate
5. Apply transform to every vertex
6. Project using inventory slot center and focal length
7. Cull backfaces
8. Compute face lighting
9. Convert packed HSL colors to RGB
10. Draw faces into a high-res image
11. Downsample to the final preview image
12. Display that image directly in the editor
```

That is the current renderer behavior the editor now relies on.
