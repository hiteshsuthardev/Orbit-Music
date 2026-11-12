/**
 * Orbit AI proxy (Cloudflare Worker).
 *
 * Release builds of Orbit Music don't contain the Groq API key. They send their chat request
 * here; this worker adds the key and forwards it to Groq, then returns Groq's answer unchanged.
 *
 * Secrets (Cloudflare → Worker → Settings → Variables and Secrets, type "Secret"):
 *   GROQ_API_KEY     the Groq key
 *   ORBIT_APP_TOKEN  must match AI_PROXY_TOKEN in local.properties (sent as X-Orbit-Token)
 *
 * The app token only keeps casual callers out (it ships inside the APK). Per-user request
 * limits are enforced in the app.
 */

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const MAX_BODY_BYTES = 64 * 1024;
const MAX_TOKENS = 4096;

function error(status, message) {
  return new Response(JSON.stringify({ error: { message } }), {
    status,
    headers: { "content-type": "application/json" },
  });
}

export default {
  async fetch(request, env) {
    if (request.method !== "POST") return error(405, "Use POST.");
    if (!env.GROQ_API_KEY || !env.ORBIT_APP_TOKEN) return error(503, "Proxy is not set up.");
    if (request.headers.get("x-orbit-token") !== env.ORBIT_APP_TOKEN) return error(403, "Not allowed.");

    const text = await request.text();
    if (text.length > MAX_BODY_BYTES) return error(413, "Request too large.");

    let body;
    try {
      body = JSON.parse(text);
    } catch {
      return error(400, "Invalid JSON.");
    }
    if (typeof body.model !== "string" || !Array.isArray(body.messages)) {
      return error(400, "Missing model or messages.");
    }
    body.stream = false;
    body.max_tokens = Math.min(Number(body.max_tokens) || MAX_TOKENS, MAX_TOKENS);

    const groq = await fetch(GROQ_URL, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${env.GROQ_API_KEY}`,
      },
      body: JSON.stringify(body),
    });

    // Pass status, body and Retry-After through: the app relies on them (429, json_schema fallback).
    const headers = { "content-type": "application/json" };
    const retryAfter = groq.headers.get("retry-after");
    if (retryAfter) headers["retry-after"] = retryAfter;
    // A rejected Groq key must not look like a rejected app token to the app.
    const status = groq.status === 401 ? 502 : groq.status;
    return new Response(await groq.text(), { status, headers });
  },
};
