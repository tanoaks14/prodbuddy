---
name: meta-gen
description: Generates, analyzes, and refines a high-complexity diagnostic recipe.
---

## step-1-draft-recipe
tool: agent
operation: generate_recipe
objective: |
  Create a high-complexity, 10-step investigation recipe for a distributed payment system.
  VALID TOOLS & OPERATIONS (MANDATORY):
  - splunk: search, oneshot
  - newrelic: query, query_metrics, get_trace, list_apps
  - elasticsearch: search
  - codecontext: summary, search, incident_report, call_chain, complexity_report
  - http: get, post
  - agent: think, validate_recipe

  CONSTRAINTS:
  - Start by searching Splunk with ${input.transactionId}.
  - Chain results using ${stepName.field} (e.g. use data from Step 1 in Step 2).
  - Use codecontext.call_chain for deep analysis of the identified method.
  - The final step MUST be a summary analysis using agent.think.
  - Return ONLY the recipe markdown.

## step-2-critique-draft
tool: agent
operation: think
prompt: |
  Analyze the generated recipe from step-1. 
  Check if all tools and operations match the VALID list provided in Step 1.
  Identify any logical gaps, missing error handling steps, or opportunities to use deeper code context.
  Suggest specific improvements to make the variables flow more naturally between tools.
recipe_draft: ${step-1-draft-recipe.recipe}

## step-3-refine-recipe
tool: agent
operation: think
prompt: |
  Rewrite the recipe provided in 'original_recipe' by incorporating the 'improvements' suggested.
  Ensure the final output is a single, valid Markdown recipe.
  MANDATORY: 
  - Return ONLY the markdown content.
  - Do NOT include any conversational text or preamble.
  - Start directly with the first step header (e.g. ## step-1-...).
original_recipe: ${step-1-draft-recipe.recipe}
improvements: ${step-2-critique-draft.opinion}

## step-4-validate-final
tool: agent
operation: validate_recipe
recipe: ${step-3-refine-recipe.opinion}


