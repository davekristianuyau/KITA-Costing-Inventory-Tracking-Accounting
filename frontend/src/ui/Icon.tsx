// Icon helper — resolves a lucide icon by name (as used in the service manifest) with a safe fallback.
import { icons, Box, type LucideProps } from "lucide-react";

export type IconName = keyof typeof icons;

export interface IconProps extends LucideProps {
  name: string;
}

export default function Icon({ name, ...rest }: IconProps) {
  const Cmp = (icons as Record<string, React.ComponentType<LucideProps>>)[name] ?? Box;
  return <Cmp aria-hidden {...rest} />;
}
