export function Logo({ className = "w-8 h-8" }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 200 200"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <linearGradient id="packageGradient" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#2563eb" />
          <stop offset="100%" stopColor="#1e40af" />
        </linearGradient>
      </defs>
      <path
        d="M 100 50 L 150 75 L 100 100 L 50 75 Z"
        fill="#3b82f6"
        stroke="#1e40af"
        strokeWidth="2"
      />
      <path
        d="M 50 75 L 50 135 L 100 160 L 100 100 Z"
        fill="#2563eb"
        stroke="#1e40af"
        strokeWidth="2"
        opacity="0.9"
      />
      <path
        d="M 100 100 L 100 160 L 150 135 L 150 75 Z"
        fill="#1e40af"
        stroke="#1e40af"
        strokeWidth="2"
      />
      <path
        d="M 100 50 L 100 100"
        stroke="#eff6ff"
        strokeWidth="4"
        opacity="0.6"
      />
      <path
        d="M 75 62.5 L 125 87.5"
        stroke="#eff6ff"
        strokeWidth="4"
        opacity="0.6"
      />
      <rect
        x="60"
        y="100"
        width="30"
        height="12"
        rx="2"
        fill="#eff6ff"
        opacity="0.7"
      />
      <line
        x1="63"
        y1="104"
        x2="87"
        y2="104"
        stroke="#1e40af"
        strokeWidth="1.5"
      />
      <line
        x1="63"
        y1="108"
        x2="82"
        y2="108"
        stroke="#1e40af"
        strokeWidth="1.5"
      />
      <circle cx="120" cy="100" r="6" fill="#eff6ff" opacity="0.5" />
      <circle cx="120" cy="115" r="6" fill="#eff6ff" opacity="0.5" />
      <circle cx="135" cy="107.5" r="6" fill="#eff6ff" opacity="0.5" />
      <g opacity="0.7">
        <line
          x1="100"
          y1="50"
          x2="100"
          y2="30"
          stroke="#3b82f6"
          strokeWidth="2"
          strokeDasharray="4 2"
        />
        <circle
          cx="100"
          cy="25"
          r="5"
          fill="#2563eb"
          stroke="#3b82f6"
          strokeWidth="2"
        />
        <line
          x1="150"
          y1="75"
          x2="165"
          y2="65"
          stroke="#3b82f6"
          strokeWidth="2"
          strokeDasharray="4 2"
        />
        <circle
          cx="170"
          cy="60"
          r="5"
          fill="#2563eb"
          stroke="#3b82f6"
          strokeWidth="2"
        />
        <line
          x1="50"
          y1="75"
          x2="35"
          y2="65"
          stroke="#3b82f6"
          strokeWidth="2"
          strokeDasharray="4 2"
        />
        <circle
          cx="30"
          cy="60"
          r="5"
          fill="#2563eb"
          stroke="#3b82f6"
          strokeWidth="2"
        />
      </g>
      <circle
        cx="135"
        cy="145"
        r="18"
        fill="#2563eb"
        stroke="#eff6ff"
        strokeWidth="3"
      />
      <text
        x="135"
        y="152"
        fontSize="20"
        fontWeight="bold"
        fill="#eff6ff"
        textAnchor="middle"
      >
        S
      </text>
    </svg>
  )
}
