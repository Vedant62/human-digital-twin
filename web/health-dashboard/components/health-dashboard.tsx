"use client";

import { useState, useEffect } from "react";
import axios from "axios";
import { HumanAvatar } from "@/components/human-avatar";
import { StatCard } from "@/components/stat-card";
import { Card } from "@/components/ui/card";
import { Heart, Activity, Footprints, Flame, Moon, Timer } from "lucide-react";

// Health data structure
export interface HealthData {
  heartRate: number;
  steps: number;
  calories: number;
  sleep: string;
  activity: {
    minutes: number;
    intensity: number;
  };
  stress: number;
}

export function HealthDashboard() {
  const [healthData, setHealthData] = useState<HealthData>({
    heartRate: 72,
    steps: 8432,
    calories: 1850,
    sleep: "",
    activity: {
      minutes: 45,
      intensity: 72,
    },
    stress: 32,
  });

  // New states for prediction
  const [prediction, setPrediction] = useState<string>("");
  const [accuracy, setAccuracy] = useState<string>("");
  const [isPredicting, setIsPredicting] = useState(false);

  useEffect(() => {
    const fetchHealthData = async () => {
      try {
        const response = await axios.get("/api/health-data");
        if (!response.data) {
          console.log("Response data is empty!");
          return;
        }

        const data = response.data;
        console.log("Fetched health data:", data);

        // Map API data to our health data structure
        setHealthData({
          heartRate: data.bpm || 0,
          steps: data.steps || 0,
          calories: data.calories || 0,
          sleep: data.sleep,
          activity: {
            minutes: data.activityMinutes || 45,
            intensity: data.activityIntensity || 72,
          },
          stress: data.stress || 32,
        });
      } catch (error) {
        console.error("Error fetching health data:", error);
      }
    };

    fetchHealthData(); // Initial fetch
    const intervalId = setInterval(fetchHealthData, 5000); // Fetch every 5 seconds

    return () => clearInterval(intervalId); // Cleanup on component unmount
  }, []);

  // Function to predict health based on BPM
  const handlePredictHealth = async () => {
    setIsPredicting(true);
    setPrediction("");
    setAccuracy("");

    try {
      const res = await fetch("/api/run-model", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ bpm: Number(healthData.heartRate) }),
      });

      const data = await res.json();

      if (data.success) {
        // Parse the output to extract accuracy and predicted health
        const outputLines = data.output.split("\n");
        const accuracyLine = outputLines.find((line: string) =>
          line.startsWith("Accuracy:")
        );
        const predictionLine = outputLines.find((line: string) =>
          line.startsWith("Predicted Health:")
        );

        if (accuracyLine) {
          setAccuracy(accuracyLine.split(":")[1].trim());
        }

        if (predictionLine) {
          const healthValue = predictionLine.split(":")[1].trim();
          setPrediction(healthValue === "1" ? "Healthy" : "Not Healthy");
        } else {
          setPrediction(data.output);
        }
      } else {
        setPrediction(`Error: ${data.error}`);
      }
    } catch (error) {
      console.error("Error predicting health:", error);
      setPrediction("Failed to predict health");
    } finally {
      setIsPredicting(false);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <Card className="col-span-1 bg-white-800/50 border-black backdrop-blur-sm p-6 flex flex-col items-center justify-center">
        <h2 className="text-xl font-semibold mb-6 text-black-200">
          Your Health Profile
        </h2>
        <HumanAvatar healthData={healthData} />

        {/* Predict Health Button */}
        <div className="mt-6 text-center">
          <button
            onClick={handlePredictHealth}
            disabled={isPredicting}
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors disabled:opacity-50"
          >
            {isPredicting ? "Predicting..." : "Predict Health"}
          </button>

          {prediction && (
            <div className="mt-4">
              <div
                className={`text-lg font-bold ${
                  prediction === "Healthy" ? "text-green-500" : "text-red-500"
                }`}
              >
                {prediction}
              </div>
              {accuracy && (
                <div className="text-sm text-gray-600">
                  Accuracy: {accuracy}
                </div>
              )}
            </div>
          )}
        </div>
      </Card>

      <div className="col-span-1 lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4">
        <StatCard
          title="Heart Rate"
          value={`${healthData.heartRate}`}
          unit="bpm"
          icon={<Heart className="h-5 w-5 text-rose-400" />}
          color="rose"
        />

        <StatCard
          title="Steps"
          value={healthData.steps.toLocaleString()}
          unit="steps"
          icon={<Footprints className="h-5 w-5 text-blue-400" />}
          color="blue"
        />

        <StatCard
          title="Calories"
          value={healthData.calories.toLocaleString()}
          unit="kcal"
          icon={<Flame className="h-5 w-5 text-amber-400" />}
          color="amber"
        />

        <StatCard
          title="Sleep"
          value={healthData.sleep}
          unit=""
          icon={<Moon className="h-5 w-5 text-indigo-400" />}
          color="indigo"
        />

        <StatCard
          title="Activity"
          value={healthData.activity.minutes.toString()}
          unit="min"
          icon={<Activity className="h-5 w-5 text-emerald-400" />}
          color="emerald"
          subtitle={`${healthData.activity.intensity}% intensity`}
        />

        <StatCard
          title="Stress Level"
          value={healthData.stress.toString()}
          unit="%"
          icon={<Timer className="h-5 w-5 text-purple-400" />}
          color="purple"
        />
      </div>
    </div>
  );
}
