import { Type } from "@earendil-works/pi-ai";
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

export default function (pi: ExtensionAPI) {
  pi.registerTool({
    name: "plan",
    label: "Plan",
    description: "Update the session plan. Send every step with its current done flag.",
    parameters: Type.Object({
      steps: Type.Array(Type.Object({ text: Type.String(), done: Type.Boolean() })),
    }),
    async execute(_toolCallId, { steps }) {
      const text = steps.map((step) => `[${step.done ? "x" : " "}] ${step.text}`).join("\n");
      return { content: [{ type: "text", text }], details: { steps } };
    },
  });
}
