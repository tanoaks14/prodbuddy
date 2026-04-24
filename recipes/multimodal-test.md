---
name: multimodal-test
description: Test agent multimodal capabilities with a sample image
tags: [test, multimodal]
---

## download-test-image
tool: http
operation: download_base64
url: "https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png"

## analyze-image
tool: agent
operation: think
prompt: "What colors are in this logo? Describe what you see."
image: "${download-test-image.base64}"
