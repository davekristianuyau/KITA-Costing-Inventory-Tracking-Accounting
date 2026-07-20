/** @type {import('tailwindcss').Config} */
// Colors map to CSS variables (RGB channels) so light/dark swap via `data-theme` without changing markup.
const withOpacity = (v) => `rgb(var(${v}) / <alpha-value>)`;

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: ['selector', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        bg: withOpacity("--bg"),
        surface: withOpacity("--surface"),
        card: withOpacity("--card"),
        border: withOpacity("--border"),
        text: withOpacity("--text"),
        muted: withOpacity("--muted"),
        primary: {
          DEFAULT: withOpacity("--primary"),
          fg: withOpacity("--primary-fg"),
        },
        ring: withOpacity("--ring"),
        danger: withOpacity("--danger"),
        success: withOpacity("--success"),
      },
      borderRadius: {
        sm: "var(--radius-sm)",
        DEFAULT: "var(--radius)",
        lg: "var(--radius-lg)",
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "-apple-system", "Segoe UI", "Roboto", "sans-serif"],
      },
    },
  },
  plugins: [],
};
