"use client";
import { useEffect, useRef } from "react";
import { HealthData } from "./health-dashboard";

interface HumanAvatarProps {
  healthData: HealthData;
}

export function HumanAvatar({ healthData }: HumanAvatarProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Calculate health score (simplified)
    const healthScore = calculateHealthScore(healthData);

    // Draw health aura
    drawHealthAura(ctx, canvas.width, canvas.height, healthScore);

    // Load and draw the avatar image
    const avatarImage = new Image();
    avatarImage.src = "/human.png"; // Path to your image in the public directory
    avatarImage.onload = () => {
      const scaleFactor = 1.5; // Adjust this factor to make the image larger or smaller
      const imageWidth = avatarImage.width * scaleFactor;
      const imageHeight = avatarImage.height * scaleFactor;
      const centerX = (canvas.width - imageWidth) / 2;
      const centerY = (canvas.height - imageHeight) / 2;
      ctx.drawImage(avatarImage, centerX, centerY, imageWidth, imageHeight);
    };
  }, [healthData]);

  const calculateHealthScore = (data: HealthData): number => {
    // Ensure all values are numbers
    const heartRate = isNaN(data.heartRate) ? 70 : data.heartRate;
    const steps = isNaN(data.steps) ? 0 : data.steps;

    // Map sleep quality to a score
    const sleepScore = 100;
    const activityScore = data.activity.intensity;
    const stressScore = 100 - data.stress;

    // Simplified health score calculation (0-100)
    const heartRateScore = Math.max(0, 100 - Math.abs(heartRate - 70) * 2);
    const stepsScore = Math.min(100, (steps / 10000) * 100);

    const totalScore =
      heartRateScore + stepsScore + sleepScore + activityScore + stressScore;
    const healthScore = totalScore / 5;

    return isNaN(healthScore) ? 0 : healthScore; // Default to 0 if NaN
  };

  const drawHealthAura = (
    ctx: CanvasRenderingContext2D,
    width: number,
    height: number,
    healthScore: number
  ) => {
    const centerX = width / 2;
    const centerY = height / 2;
    const radius = 100;

    // Create gradient
    const gradient = ctx.createRadialGradient(
      centerX,
      centerY,
      radius * 0.5,
      centerX,
      centerY,
      radius
    );

    // Set color based on health score
    const hue = Math.min(120, healthScore * 1.2); // 0-120 (red to green)
    gradient.addColorStop(0, `hsla(${hue}, 80%, 60%, 0.3)`);
    gradient.addColorStop(1, `hsla(${hue}, 80%, 60%, 0)`);

    // Draw aura
    ctx.beginPath();
    ctx.fillStyle = gradient;
    ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
    ctx.fill();
  };

  return (
    <div className="relative w-full max-w-xs aspect-square">
      <canvas
        ref={canvasRef}
        width={300}
        height={300}
        className="w-full h-full"
      />
      <div className="absolute bottom-4 left-0 right-0 text-center text-sm text-black">
        Tap to view details
      </div>
    </div>
  );
}
