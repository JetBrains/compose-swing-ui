// A pi extension that asks the client before a tool changes files or runs a command.
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

const ASKED = new Set(["write", "edit", "bash"]);

function describe(tool: string, input: Record<string, any>): string {
  if (tool === "bash") return String(input.command);
  const path = `Path: ${input.path}`;
  if (tool === "write") return `${path}\nContent:\n${input.content}`;
  const edits = Array.isArray(input.edits) ? input.edits : [input];
  return [path, ...edits.map((edit) => `-${edit.oldText}\n+${edit.newText}`)].join("\n");
}

export default function (pi: ExtensionAPI) {
  pi.on("tool_call", async (event, ctx) => {
    if (!ASKED.has(event.toolName)) return undefined;
    const allowed = await ctx.ui.confirm(`Allow tool: ${event.toolName}`, describe(event.toolName, event.input));
    return allowed ? undefined : { block: true, reason: "Denied by the user" };
  });
}
