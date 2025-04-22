import { HealthDashboard } from "@/components/health-dashboard"

export default function Home() {
  return (
    <main className="min-h-screen bg-gradient-to-b from-white-900 to-white-950 text-black">
      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold tracking-tight mb-8 text-center">Health Dashboard</h1>
        <HealthDashboard />
      </div>
    </main>
  )
}
