// Tiny className joiner — filters falsy values so conditional classes read cleanly.
export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(" ");
}
