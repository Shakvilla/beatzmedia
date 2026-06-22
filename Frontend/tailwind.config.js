/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // BeatzClik Palette
        beatz: {
          green: {
            DEFAULT: '#1ED760',
            light: '#1A9E48',
          },
          gold: '#E8B84B',
          red: {
            DEFAULT: '#E22134',
            light: '#C8192A',
          },
          blue: {
            DEFAULT: '#2E77F0',
            light: '#2563D4',
          },
          dark: {
            bg: '#121212',
            surface: '#181818',
            'surface-2': '#282828',
            'surface-3': '#2A2A2A',
          },
          light: {
            bg: '#FAF7F2',
            surface: '#FFFFFF',
            'surface-2': '#F0EBE2',
            'surface-3': '#E8E2D8',
          }
        }
      },
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      fontSize: {
        'display': ['2.25rem', { lineHeight: '2.5rem', fontWeight: '800' }], // 36px
        'title': ['1.5rem', { lineHeight: '2rem', fontWeight: '800' }],    // 24px
        'body-strong': ['0.875rem', { lineHeight: '1.25rem', fontWeight: '700' }], // 14px
        'body': ['0.875rem', { lineHeight: '1.25rem', fontWeight: '400' }],       // 14px
      }
    },
  },
  plugins: [],
}
