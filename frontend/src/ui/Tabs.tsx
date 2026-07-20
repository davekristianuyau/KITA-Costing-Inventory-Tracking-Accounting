// Tabs primitive — thin token-styled wrapper over Radix Tabs (roving focus, arrow-key nav, aria-selected).
import * as RadixTabs from "@radix-ui/react-tabs";
import { forwardRef } from "react";
import { cn } from "./cn";

export const Tabs = RadixTabs.Root;

export const TabsList = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof RadixTabs.List>
>(function TabsList({ className, ...rest }, ref) {
  return (
    <RadixTabs.List
      ref={ref}
      className={cn("flex items-center gap-1 border-b border-border", className)}
      {...rest}
    />
  );
});

export const TabsTrigger = forwardRef<
  HTMLButtonElement,
  React.ComponentPropsWithoutRef<typeof RadixTabs.Trigger>
>(function TabsTrigger({ className, ...rest }, ref) {
  return (
    <RadixTabs.Trigger
      ref={ref}
      className={cn(
        "inline-flex items-center gap-2 border-b-2 border-transparent px-3 py-2 text-sm font-medium text-muted",
        "-mb-px hover:text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-t",
        "data-[state=active]:border-primary data-[state=active]:text-text",
        className,
      )}
      {...rest}
    />
  );
});

export const TabsContent = RadixTabs.Content;
