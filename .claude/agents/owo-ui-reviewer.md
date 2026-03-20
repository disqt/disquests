---
name: owo-ui-reviewer
description: Review owo-ui code for common pitfalls specific to v0.13.0+1.21.11. Use after modifying any owo-ui screen, component, or XML model.
---

# owo-ui Code Reviewer

Review changes to owo-ui screens, components, and XML models for known pitfalls.

## Check each of these:

### XML Model Issues
- [ ] Root component wrapped in `<components>` tag (not directly under `<owo-ui>`)
- [ ] Scroll container's child is the FIRST element (before `<sizing>`, `<surface>`, `<padding>`)
- [ ] No empty `<children/>` tags in flow layouts (can cause parse errors)
- [ ] All interactive components have `id` attributes for `childById()` lookup

### Coordinate Issues
- [ ] `onMouseDown` uses `click.x()`/`click.y()` directly (NOT `click.x() - this.x()`)
- [ ] Click constructor uses `MouseInput(button, modifiers)` not bare int
- [ ] E2E tests multiply coordinates by `getWindow().getScaleFactor()` for GLFW input

### API v0.13.0 Names
- [ ] Using `BaseUIComponent` not `BaseComponent`
- [ ] Using `UIComponents` not `Components`
- [ ] Using `UIContainers` not `Containers`
- [ ] Using `OwoUIGraphics` not `OwoUIDrawContext`

### Visibility
- [ ] No `.visible` or `.setVisible()` calls (owo-ui has no visibility API)
- [ ] Using `sizing(Sizing.fixed(0), Sizing.fixed(0))` to hide components
- [ ] Using `sizing(Sizing.content(), Sizing.content())` to show them back

### Performance
- [ ] `draw()` / `render()` methods don't allocate `Text.literal()` every frame
- [ ] Static text cached as fields or constants
- [ ] `buildLocationString()` and similar called in constructor, not per-frame

### Testing
- [ ] E2E click tests use `context.getInput().setCursorPos()` + `pressMouse()`, NOT direct `screen.mouseClicked()`
- [ ] TestLogCapture assertions verify the full dispatch chain fired
- [ ] Quests added to ClientCache before opening screens (prevents auto-close)
