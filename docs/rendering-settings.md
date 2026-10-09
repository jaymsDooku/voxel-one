# Rendering settings

Press Escape, then choose **Rendering (R)** or press R. Use Up/Down or the mouse wheel to select a setting. Left/Right changes it. Click the left or right side of a row to decrease or increase it. All rows remain reachable by scrolling in a small window.

Changes apply immediately and save to `.voxel-one/rendering.properties` in your home folder. Escape returns to Controls; Escape again resumes play. If saving fails, the screen says so and the live change still applies.

**Low cost (1)** lowers render scale to 50%, uses low atmosphere quality, and turns off world shadows, light particles, ambient occlusion, contact shadows, screen reflections, screen global illumination, volumetric light, bloom, and clouds. **Defaults (2)** restores the renderer's standard quality values. Lower quality may help GPU load; it does not promise to fix CPU, world generation, or network delays.

Dynamic resolution changes render scale between 50% and 100% to meet the frame budget. The frame budget is in milliseconds: 16.67 ms is about 60 frames per second; 33.33 ms is about 30. Manual exposure applies when Auto exposure is off. Fog density and overview haze are separate controls. Atmosphere quality controls atmosphere lookup table resolution and sampling.

The screen exposes all runtime `RenderSettings` fields. OpenGL backend selection and MSAA allocation remain startup options; they cannot be changed live on this screen.
