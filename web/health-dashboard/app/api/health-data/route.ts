import { NextResponse } from "next/server";
import axios from "axios";

export async function GET() {
  try {
    const response = await axios.get(process.env.GET_HEALTH_URL!);
    return NextResponse.json(response.data);
  } catch (error) {
    console.error("Error fetching health data:", error);
    return NextResponse.json(
      { error: "Failed to fetch health data" },
      { status: 500 }
    );
  }
}
