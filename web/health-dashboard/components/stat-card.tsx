import type { ReactNode } from "react"
import { Card, CardContent } from "@/components/ui/card"

interface StatCardProps {
  title: string
  value: string
  unit: string
  icon: ReactNode
  color: "rose" | "blue" | "amber" | "indigo" | "emerald" | "purple"
  subtitle?: string
}

export function StatCard({ title, value, unit, icon, color, subtitle }: StatCardProps) {
  const colorMap = {
    rose: "from-rose-500/20 to-rose-500/5 border-rose-500/20",
    blue: "from-blue-500/20 to-blue-500/5 border-blue-500/20",
    amber: "from-amber-500/20 to-amber-500/5 border-amber-500/20",
    indigo: "from-indigo-500/20 to-indigo-500/5 border-indigo-500/20",
    emerald: "from-emerald-500/20 to-emerald-500/5 border-emerald-500/20",
    purple: "from-purple-500/20 to-purple-500/5 border-purple-500/20",
  }

  return (
    <Card className={`bg-gradient-to-b ${colorMap[color]} border-gray-800 backdrop-blur-sm overflow-hidden`}>
      <CardContent className="p-6">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm font-medium text-black-300">{title}</h3>
          <div className="rounded-full bg-black p-1.5">{icon}</div>
        </div>
        <div className="flex items-baseline">
          <span className="text-2xl font-bold mr-1">{value}</span>
          <span className="text-sm text-gray-400">{unit}</span>
        </div>
        {subtitle && <p className="text-xs text-gray-400 mt-1">{subtitle}</p>}
      </CardContent>
    </Card>
  )
}
