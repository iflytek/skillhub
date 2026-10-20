/** shadcn/ui (MIT), New York/Radix components adapted to scoped DSH CSS.
 * Source: https://ui.shadcn.com/r/styles/new-york/{button,card,input,avatar,tabs,dialog}.json
 * Utility classes are replaced by astron-sh-* classes; React and ReactDOM remain host externals.
 */
import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import * as AvatarPrimitive from '@radix-ui/react-avatar';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import * as SelectPrimitive from '@radix-ui/react-select';
import * as TabsPrimitive from '@radix-ui/react-tabs';

const classes = (...values: (string | undefined)[]) => values.filter(Boolean).join(' ');
const buttonVariants = cva('astron-sh-button', {
  variants: {
    variant: { default: 'astron-sh-button-default', outline: 'astron-sh-button-outline', ghost: 'astron-sh-button-ghost', destructive: 'astron-sh-button-destructive' },
    size: { default: 'astron-sh-button-size-default', sm: 'astron-sh-button-size-sm', icon: 'astron-sh-button-size-icon' },
  }, defaultVariants: { variant: 'outline', size: 'default' },
});
export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> { asChild?: boolean }
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant, size, asChild = false, type = 'button', ...props }, ref) => {
  const Component = asChild ? Slot : 'button';
  return <Component ref={ref} type={type} className={classes(buttonVariants({ variant, size }), className)} {...props} />;
});
Button.displayName = 'Button';
export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(({ className, ...props }, ref) =>
  <input ref={ref} className={classes('astron-sh-input', className)} {...props} />);
Input.displayName = 'Input';
export const Card = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) =>
  <div ref={ref} className={classes('astron-sh-card', className)} {...props} />);
Card.displayName = 'Card';
export const CardHeader = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) =>
  <div ref={ref} className={classes('astron-sh-card-header', className)} {...props} />);
CardHeader.displayName = 'CardHeader';
export const CardTitle = React.forwardRef<HTMLHeadingElement, React.HTMLAttributes<HTMLHeadingElement>>(({ className, ...props }, ref) =>
  <h3 ref={ref} className={classes('astron-sh-card-title', className)} {...props} />);
CardTitle.displayName = 'CardTitle';
export const CardDescription = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(({ className, ...props }, ref) =>
  <p ref={ref} className={classes('astron-sh-card-description', className)} {...props} />);
CardDescription.displayName = 'CardDescription';
export const CardContent = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) =>
  <div ref={ref} className={classes('astron-sh-card-content', className)} {...props} />);
CardContent.displayName = 'CardContent';
export const CardFooter = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) =>
  <div ref={ref} className={classes('astron-sh-card-footer', className)} {...props} />);
CardFooter.displayName = 'CardFooter';
export const Avatar = React.forwardRef<React.ElementRef<typeof AvatarPrimitive.Root>, React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Root>>(
  ({ className, ...props }, ref) => <AvatarPrimitive.Root ref={ref} className={classes('astron-sh-avatar', className)} {...props} />);
Avatar.displayName = 'Avatar';
export const AvatarImage = AvatarPrimitive.Image;
export const AvatarFallback = AvatarPrimitive.Fallback;
export const Tabs = TabsPrimitive.Root;
export const TabsList = React.forwardRef<React.ElementRef<typeof TabsPrimitive.List>, React.ComponentPropsWithoutRef<typeof TabsPrimitive.List>>(
  ({ className, ...props }, ref) => <TabsPrimitive.List ref={ref} className={classes('astron-sh-tabs-list', className)} {...props} />);
TabsList.displayName = 'TabsList';
export const TabsTrigger = React.forwardRef<React.ElementRef<typeof TabsPrimitive.Trigger>, React.ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger>>(
  ({ className, ...props }, ref) => <TabsPrimitive.Trigger ref={ref} className={classes('astron-sh-tabs-trigger', className)} {...props} />);
TabsTrigger.displayName = 'TabsTrigger';
export const TabsContent = TabsPrimitive.Content;
export const Dialog = DialogPrimitive.Root;
export const DialogTitle = DialogPrimitive.Title;
export const DialogDescription = DialogPrimitive.Description;
export function DialogContent({ container, children, ...props }: React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> & { container: HTMLElement | null }) {
  return <DialogPrimitive.Portal container={container}>
    <DialogPrimitive.Overlay className="astron-sh-dialog-overlay" />
    <DialogPrimitive.Content className="astron-sh-dialog" {...props}>{children}</DialogPrimitive.Content>
  </DialogPrimitive.Portal>;
}

/** shadcn/ui Select (MIT), New York registry select.json; scoped CSS replaces utilities. */
export const SelectField = React.forwardRef<HTMLButtonElement, {
  label: string; value: string; onValueChange(value: string): void; disabled?: boolean;
  options: readonly { value: string; label: string; disabled?: boolean }[];
}>(({ label, value, onValueChange, disabled, options }, ref) => {
  const [container, setContainer] = React.useState<HTMLSpanElement | null>(null);
  return <span className="astron-sh-select-root" ref={setContainer}>
    <SelectPrimitive.Root value={value} onValueChange={onValueChange} disabled={disabled}>
      <SelectPrimitive.Trigger ref={ref} aria-label={label} className="astron-sh-select-trigger">
        <SelectPrimitive.Value /><SelectPrimitive.Icon asChild><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg></SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>
      <SelectPrimitive.Portal container={container}>
        <SelectPrimitive.Content position="popper" sideOffset={5} className="astron-sh-select-content">
          <SelectPrimitive.ScrollUpButton className="astron-sh-select-scroll" aria-label="向上滚动">⌃</SelectPrimitive.ScrollUpButton>
          <SelectPrimitive.Viewport><SelectPrimitive.Group>
            {options.map(option => <SelectPrimitive.Item key={option.value} value={option.value} disabled={option.disabled} className="astron-sh-select-item">
              <SelectPrimitive.ItemText>{option.label}</SelectPrimitive.ItemText>
              <SelectPrimitive.ItemIndicator aria-hidden="true">✓</SelectPrimitive.ItemIndicator>
            </SelectPrimitive.Item>)}
          </SelectPrimitive.Group></SelectPrimitive.Viewport>
          <SelectPrimitive.ScrollDownButton className="astron-sh-select-scroll" aria-label="向下滚动">⌄</SelectPrimitive.ScrollDownButton>
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  </span>;
});
SelectField.displayName = 'SelectField';

/** shadcn Skeleton structure, using local DSH semantic colors instead of utility classes. */
export function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div {...props} aria-hidden="true" className={classes('astron-sh-skeleton', className)} />;
}
