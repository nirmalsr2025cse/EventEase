(function () {
  "use strict";

  const BASE_URL = window.EVENTEASE_API_BASE_URL || "";

  async function request(path, options, retrying) {
    const settings = options || {};
    const response = await fetch(`${BASE_URL}${path}`, {
      ...settings,
      headers: {
        ...(settings.body ? { "Content-Type": "application/json" } : {}),
        ...(settings.headers || {})
      },
      credentials: "include"
    });
    const authenticationRequest = path.startsWith("/api/auth/") && (
      path.endsWith("/login") || path === "/api/auth/login" || path === "/api/auth/student/register"
    );
    if (response.status === 401 && path !== "/api/auth/refresh" && !authenticationRequest && !retrying) {
      const refreshed = await fetch(`${BASE_URL}/api/auth/refresh`, {
        method: "POST",
        credentials: "include"
      });
      if (refreshed.ok) return request(path, options, true);
      if (refreshed.status === 401) window.location.assign("/#how-it-works");
    }
    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json")
      ? await response.json()
      : await response.text();

    if (!response.ok) {
      const message = payload && typeof payload === "object" && payload.message
        ? payload.message
        : typeof payload === "string" && payload
          ? payload
          : `Request failed (${response.status}).`;
      const requestError = new Error(message);
      requestError.status = response.status;
      requestError.data = payload && typeof payload === "object" ? payload.data : null;
      throw requestError;
    }
    return payload;
  }

  window.EventEaseAPI = Object.freeze({
    baseUrl: BASE_URL,
    get: (path) => request(path, { method: "GET" }),
    post: (path, body) => request(path, { method: "POST", ...(body === undefined ? {} : { body: JSON.stringify(body) }) }),
    put: (path, body) => request(path, { method: "PUT", ...(body === undefined ? {} : { body: JSON.stringify(body) }) }),
    delete: (path) => request(path, { method: "DELETE" })
  });
})();
