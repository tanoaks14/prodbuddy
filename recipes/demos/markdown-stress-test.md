---
name: markdown-stress-test
description: Final stress test for improved Markdown rendering
---

## init
tool: observation
operation: clear

## stress_test
tool: agent
operation: think
prompt: |
  Please provide a comprehensive reasoning block that uses ALL of the following Markdown features in a single structured response:
  1. # Primary Heading
  2. ## Secondary Heading
  3. **Bold text** with stars
  4. __Bold text__ with underscores
  5. *Italic text* with stars
  6. _Italic text_ with underscores
  7. ***Bold-Italic text*** with triple stars
  8. `Monospace code block`
  9. - List item with dash
  10. * List item with star
  
  The content should explain why detailed formatting reduces developer fatigue.

## mermaid
tool: observation
operation: mermaid

## render
tool: observation
operation: render
format: png
