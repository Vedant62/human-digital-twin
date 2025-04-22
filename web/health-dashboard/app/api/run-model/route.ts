import { NextResponse } from "next/server";
import { spawn } from "child_process";

export async function POST(req: Request) {
  const body = await req.json();
  const bpm = body.bpm;

  if (typeof bpm !== "number") {
    return NextResponse.json(
      { success: false, error: "Invalid BPM" },
      { status: 400 }
    );
  }

  const pythonPath = "/Users/vedantsharma/health-env/bin/python3";
  const scriptPath = "/Users/vedantsharma/Desktop/modelml/main.py";

  return new Promise((resolve) => {
    const pythonProcess = spawn(pythonPath, [scriptPath, bpm.toString()]);

    let output = "";
    let errorOutput = "";

    pythonProcess.stdout.on("data", (data) => {
      output += data.toString();
    });

    pythonProcess.stderr.on("data", (data) => {
      errorOutput += data.toString();
    });

    pythonProcess.on("close", (code) => {
      if (code === 0) {
        resolve(NextResponse.json({ success: true, output }));
      } else {
        resolve(
          NextResponse.json(
            {
              success: false,
              error: errorOutput || `Exited with code ${code}`,
            },
            { status: 500 }
          )
        );
      }
    });

    pythonProcess.on("error", (err) => {
      resolve(
        NextResponse.json(
          { success: false, error: err.message },
          { status: 500 }
        )
      );
    });
  });
}
