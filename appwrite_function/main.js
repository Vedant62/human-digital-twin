import { Client, Users } from 'node-appwrite';

let latestHealthData = {
	bpm:null,
	steps:null,
	calories:null,
	sleep:null
}

export default async ({ req, res, log, error }) => {
	const client = new Client()
    	.setEndpoint(process.env.APPWRITE_FUNCTION_API_ENDPOINT)
    	.setProject(process.env.APPWRITE_FUNCTION_PROJECT_ID)
    	.setKey(req.headers['x-appwrite-key'] ?? '');
  	const users = new Users(client);
	
	try {
    		const response = await users.list();
    		log(`Total users: ${response.total}`);
  	} catch (err) {
  		error("Could not list users: " + err.message);
  	}

  	if (req.path === "/ping") {
    		return res.text("Pong");
  	}

  	// Type-safe handler for each key
  	const validateAndStore = (key, type) => {
    	const value = req.body?.value;
    	if (value === undefined) return res.text(`No value provided for ${key}`);

   	if (type === 'float' && typeof value === 'number') {
      		latestHealthData[key] = value;
      		return res.text(`${key} updated: ${value}`);
    	}

    	if (type === 'int' && Number.isInteger(value)) {
      		latestHealthData[key] = value;
      		return res.text(`${key} updated: ${value}`);
    	}

    	if (type === 'string' && typeof value === 'string') {
      		latestHealthData[key] = value;
      		return res.text(`${key} updated: ${value}`);
    	}

    	return res.text(`Invalid type for ${key}`);
  };

  if (req.method === 'POST') {
    if (req.path === '/bpm') return validateAndStore('bpm', 'float');
    if (req.path === '/steps') return validateAndStore('steps', 'int');
    if (req.path === '/calories') return validateAndStore('calories', 'float');
    if (req.path === '/sleep') return validateAndStore('sleep', 'string');
  }

  if (req.path === '/get-health-data' && req.method === 'GET') {
    return res.json(latestHealthData);
  }

  return res.json({
    motto: "Build like a team of hundreds_",
    learn: "https://appwrite.io/docs",
    connect: "https://appwrite.io/discord",
    getInspired: "https://builtwith.appwrite.io",
  });
};
