(function () {
  "use strict";

  const panel = document.querySelector("#auth-panel");
  if (!panel || !window.EventEaseAPI) return;

  const loginForm = panel.querySelector("#login-form");
  const signupForm = panel.querySelector("#signup-form");
  const intendedDestinationKey = "eventease.intendedDestination";
  let desiredMode = "login";

  function validDestination(path) {
    if (typeof path !== "string" || !path.startsWith("/") || path.startsWith("//")) return null;
    if (path.startsWith("/student/") || path.startsWith("/admin/")) return path;
    return null;
  }

  function showMode(mode, focus) {
    const signup = mode === "signup";
    desiredMode = signup ? "signup" : "login";
    loginForm.hidden = signup;
    signupForm.hidden = !signup;
    panel.querySelectorAll("[data-auth-switch]").forEach((button) => {
      const active = button.dataset.authSwitch === mode;
      if (button.classList.contains("auth-tab")) {
        button.classList.toggle("active", active);
        button.setAttribute("aria-pressed", String(active));
      }
    });
    if (focus) {
      const target = signup
        ? signupForm.querySelector('[name="studentId"]')
        : loginForm.querySelector('[name="email"]');
      target?.focus({ preventScroll: true });
    }
  }

  function openAuthentication(mode) {
    panel.hidden = false;
    showMode(mode || "login", false);
    window.requestAnimationFrame(() => panel.scrollIntoView({ behavior: "smooth", block: "center" }));
  }

  panel.querySelectorAll("[data-auth-switch]").forEach((button) => {
    button.addEventListener("click", () => showMode(button.dataset.authSwitch, true));
  });

  document.querySelectorAll("[data-auth-mode]").forEach((link) => {
    link.addEventListener("click", () => {
      const requested = link.dataset.authMode;
      showMode(requested === "signup" ? "signup" : "login", false);
    });
  });

  function showMessage(form, message, isError) {
    const region = form.querySelector("[data-form-message]");
    if (!region) return;
    region.textContent = message || "";
    region.classList.toggle("error", Boolean(isError));
  }

  function clearErrors(form) {
    form.querySelectorAll(".form-field").forEach((field) => {
      field.classList.remove("invalid");
      const error = field.querySelector(".field-error");
      if (error) error.textContent = "";
    });
  }

  function showFieldError(form, fieldName, message) {
    const input = form.querySelector(`[name="${CSS.escape(fieldName)}"]`);
    const wrapper = input && input.closest(".form-field");
    if (!wrapper) return false;
    wrapper.classList.add("invalid");
    const error = wrapper.querySelector(".field-error");
    if (error) error.textContent = message;
    input.focus();
    return true;
  }

  async function currentSession() {
    const response = await window.EventEaseAPI.get("/api/auth/session");
    return response && response.data ? response.data : response;
  }

  function dashboardFor(role) {
    return role === "COLLEGE_ADMIN" ? "/admin/dashboard" : "/student/dashboard";
  }

  async function followProtectedLink(path, requiredRole) {
    const safePath = validDestination(path);
    if (!safePath) return;
    try {
      const session = await currentSession();
      const user = session && session.user;
      if (!session || !session.authenticated || !user || !user.role) {
        window.sessionStorage.setItem(intendedDestinationKey, safePath);
        if (window.location.pathname === "/" && window.location.hash === "#how-it-works") {
          openAuthentication("login");
          return;
        }
        window.location.assign("/#how-it-works");
        return;
      }
      if (requiredRole && user.role !== requiredRole) {
        window.location.assign(dashboardFor(user.role));
        return;
      }
      window.location.assign(safePath);
    } catch (error) {
      showMessage(loginForm, error.message || "We could not check your session. Please try again.", true);
      openAuthentication("login");
      window.location.hash = "how-it-works";
    }
  }

  document.addEventListener("click", (event) => {
    const link = event.target.closest("[data-auth-required]");
    if (!link) return;
    event.preventDefault();
    followProtectedLink(link.dataset.authRequired || link.getAttribute("href"), link.dataset.authRole);
  });

  async function submitLogin(event) {
    event.preventDefault();
    clearErrors(loginForm);
    showMessage(loginForm, "", false);
    if (!loginForm.reportValidity()) return;

    const submit = loginForm.querySelector('[type="submit"]');
    const originalLabel = submit.textContent;
    submit.disabled = true;
    submit.textContent = "Logging in…";
    try {
      const body = Object.fromEntries(new FormData(loginForm).entries());
      const result = await window.EventEaseAPI.post("/api/auth/login", body);
      const payload = result && result.data ? result.data : result;
      const role = payload && payload.role;
      if (role !== "STUDENT" && role !== "COLLEGE_ADMIN") {
        throw new Error("The server did not return a valid account role.");
      }
      const requested = validDestination(window.sessionStorage.getItem(intendedDestinationKey));
      window.sessionStorage.removeItem(intendedDestinationKey);
      const allowedDestination = requested && (
        (role === "STUDENT" && requested.startsWith("/student/"))
        || (role === "COLLEGE_ADMIN" && requested.startsWith("/admin/"))
      ) ? requested : null;
      window.location.assign(allowedDestination || dashboardFor(role));
    } catch (error) {
      showMessage(loginForm, error.message || "Could not sign in. Check your details and try again.", true);
    } finally {
      submit.disabled = false;
      submit.textContent = originalLabel;
    }
  }

  async function submitSignup(event) {
    event.preventDefault();
    clearErrors(signupForm);
    showMessage(signupForm, "", false);
    if (!signupForm.reportValidity()) return;

    const formData = new FormData(signupForm);
    const password = formData.get("password");
    if (password !== formData.get("confirmPassword")) {
      showFieldError(signupForm, "confirmPassword", "Passwords do not match.");
      showMessage(signupForm, "Passwords do not match.", true);
      return;
    }

    const submit = signupForm.querySelector('[type="submit"]');
    const originalLabel = submit.textContent;
    submit.disabled = true;
    submit.textContent = "Creating Account…";
    try {
      const body = Object.fromEntries(formData.entries());
      body.year = Number(body.year);
      const result = await window.EventEaseAPI.post("/api/auth/student/register", body);
      const message = result && result.message
        ? result.message
        : "Account created successfully. Please login to continue.";
      signupForm.reset();
      showMode("login", false);
      showMessage(loginForm, message, false);
      loginForm.querySelector('[name="email"]').focus({ preventScroll: true });
    } catch (error) {
      const response = error && error.data;
      const fieldErrors = response && typeof response === "object" ? response : null;
      const firstField = fieldErrors && Object.keys(fieldErrors)[0];
      if (firstField) {
        const inputName = firstField === "passwordConfirmed" ? "confirmPassword" : firstField;
        showFieldError(signupForm, inputName, fieldErrors[firstField]);
      }
      showMessage(signupForm,
        (firstField && fieldErrors[firstField]) || error.message || "Could not create your account. Please try again.",
        true);
    } finally {
      submit.disabled = false;
      submit.textContent = originalLabel;
    }
  }

  loginForm.addEventListener("submit", submitLogin);
  signupForm.addEventListener("submit", submitSignup);

  async function updateLandingNavigation() {
    try {
      const session = await currentSession();
      const role = session && session.authenticated && session.user && session.user.role;
      document.querySelectorAll("[data-role-link]").forEach((link) => {
        link.hidden = link.dataset.roleLink !== role;
      });
      document.querySelectorAll("[data-guest-only]").forEach((link) => {
        link.hidden = role === "STUDENT" || role === "COLLEGE_ADMIN";
      });
    } catch (error) {
      showMessage(loginForm, error.message || "Session status could not be checked.", true);
      openAuthentication("login");
    }
  }

  function handleHash() {
    if (window.location.hash !== "#how-it-works") return;
    openAuthentication(desiredMode);
  }

  if (window.location.hash === "#how-it-works") {
    handleHash();
  } else {
    panel.hidden = true;
  }
  window.addEventListener("hashchange", handleHash);
  updateLandingNavigation();
})();
