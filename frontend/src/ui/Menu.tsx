// Menu primitive — token-styled Radix dropdown menu (used for the user/identity menu in the shell).
import * as RadixMenu from "@radix-ui/react-dropdown-menu";
import { forwardRef } from "react";
import { cn } from "./cn";

export const Menu = RadixMenu.Root;
export const MenuTrigger = RadixMenu.Trigger;

export const MenuContent = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof RadixMenu.Content>
>(function MenuContent({ className, sideOffset = 6, ...rest }, ref) {
  return (
    <RadixMenu.Portal>
      <RadixMenu.Content
        ref={ref}
        sideOffset={sideOffset}
        className={cn(
          "z-50 min-w-44 rounded-lg border border-border bg-card p-1 shadow-lg",
          className,
        )}
        {...rest}
      />
    </RadixMenu.Portal>
  );
});

export const MenuItem = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof RadixMenu.Item>
>(function MenuItem({ className, ...rest }, ref) {
  return (
    <RadixMenu.Item
      ref={ref}
      className={cn(
        "flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm text-text outline-none",
        "focus:bg-bg data-[disabled]:pointer-events-none data-[disabled]:opacity-50",
        className,
      )}
      {...rest}
    />
  );
});

export const MenuLabel = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof RadixMenu.Label>
>(function MenuLabel({ className, ...rest }, ref) {
  return (
    <RadixMenu.Label
      ref={ref}
      className={cn("px-2 py-1.5 text-xs text-muted", className)}
      {...rest}
    />
  );
});

export const MenuSeparator = forwardRef<
  HTMLDivElement,
  React.ComponentPropsWithoutRef<typeof RadixMenu.Separator>
>(function MenuSeparator({ className, ...rest }, ref) {
  return (
    <RadixMenu.Separator ref={ref} className={cn("my-1 h-px bg-border", className)} {...rest} />
  );
});
