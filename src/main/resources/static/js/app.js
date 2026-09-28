(function () {
  "use strict";

  const toastRegion = document.createElement("div");
  toastRegion.className = "toast-region";
  toastRegion.setAttribute("aria-live", "polite");
  document.body.append(toastRegion);

  function toast(title, message, type) {
    const node = document.createElement("div");
    node.className = `toast ${type || ""}`;
    const heading = document.createElement("strong");
    const text = document.createElement("p");
    heading.textContent = title;
    text.textContent = message;
    node.append(heading, text);
    toastRegion.append(node);
    window.setTimeout(() => node.remove(), 4500);
  }
  window.EventEaseToast = toast;

  const app = document.querySelector(".app-shell");
  const menuToggle = document.querySelector(".menu-toggle");
  const backdrop = document.querySelector(".mobile-nav-backdrop");
  function setMenu(open) {
    if (!app || !menuToggle) return;
    app.classList.toggle("nav-open", open);
    menuToggle.setAttribute("aria-expanded", String(open));
  }
  menuToggle?.addEventListener("click", () => setMenu(!app.classList.contains("nav-open")));
  backdrop?.addEventListener("click", () => setMenu(false));

  document.querySelectorAll("[data-logout]").forEach((link) => {
    link.addEventListener("click", async (event) => {
      event.preventDefault();
      try {
        await window.EventEaseAPI.post("/api/auth/logout");
      } finally {
        window.location.href = "/";
      }
    });
  });

  if (document.body.hasAttribute("data-public-auth")) {
    window.EventEaseAPI.get("/api/auth/session").then((result) => {
      const session = result && result.data ? result.data : result;
      const user = session && (session.user || session);
      if (session && session.authenticated && user && user.role) {
        window.location.replace(user.role === "COLLEGE_ADMIN" ? "/admin/dashboard" : "/student/dashboard");
      }
    }).catch((error) => {
      if (error.status !== 401) toast("Session check failed", "Please try again. If the issue continues, contact your college administrator.", "error");
    });
  }

  document.querySelectorAll("[data-toast]").forEach((button) => {
    button.addEventListener("click", (event) => {
      if (button.tagName === "A") event.preventDefault();
      toast(button.dataset.toastTitle || "EventEase", button.dataset.toast, button.dataset.toastType || "");
    });
  });

  document.querySelectorAll("[data-open-modal]").forEach((button) => {
    button.addEventListener("click", () => {
      const modal = document.querySelector(button.dataset.openModal);
      const confirm = modal?.querySelector("[data-api-action]");
      if (confirm && button.dataset.registrationId) {
        confirm.dataset.apiPath = `/api/registrations/${button.dataset.registrationId}`;
      }
      modal?.classList.add("open");
      modal?.setAttribute("aria-hidden", "false");
    });
  });
  document.querySelectorAll("[data-close-modal]").forEach((button) => {
    button.addEventListener("click", () => {
      const modal = button.closest(".modal-backdrop");
      modal?.classList.remove("open");
      modal?.setAttribute("aria-hidden", "true");
    });
  });
  document.querySelectorAll(".modal-backdrop").forEach((modal) => {
    modal.addEventListener("click", (event) => {
      if (event.target === modal) {
        modal.classList.remove("open");
        modal.setAttribute("aria-hidden", "true");
      }
    });
  });

  document.querySelectorAll("form[data-api-form]").forEach((form) => {
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!validateForm(form)) return;
      const submit = form.querySelector('[type="submit"]');
      const original = submit && submit.innerHTML;
      if (submit) {
        submit.disabled = true;
        submit.innerHTML = '<span aria-hidden="true">◌</span> Please wait…';
      }
      try {
        const body = Object.fromEntries(new FormData(form).entries());
        delete body.confirmPassword;
        const method = (form.dataset.method || "POST").toLowerCase();
        const path = form.dataset.apiForm;
        const result = await window.EventEaseAPI[method](path, body);
        toast("Success", result && result.message ? result.message : "Your request has been completed.", "success");
        let redirect = form.dataset.redirect;
        if (form.id === "login-form") {
          const responseData = result && result.data ? result.data : result;
          const user = responseData && (responseData.user || responseData);
          const responseRole = user && user.role;
          redirect = responseRole === "COLLEGE_ADMIN" ? "/admin/dashboard" : "/student/dashboard";
        }
        if (redirect) window.location.href = redirect;
        else form.reset();
      } catch (error) {
        toast("Could not complete request", error.message || "Please try again.", "error");
      } finally {
        if (submit) {
          submit.disabled = false;
          submit.innerHTML = original;
        }
      }
    });
  });

  function validateForm(form) {
    let valid = true;
    form.querySelectorAll("[required]").forEach((field) => {
      const wrapper = field.closest(".form-field");
      let message = "";
      if (!field.value.trim()) message = "This field is required.";
      else if (field.type === "checkbox" && !field.checked) message = "Please check this box to continue.";
      else if (field.type === "email" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(field.value.trim())) message = "Enter a valid email address.";
      else if (field.name === "confirmPassword") {
        const password = form.querySelector('[name="password"]');
        if (password && field.value !== password.value) message = "Passwords do not match.";
      } else if (field.name === "password" && field.value.length < 8) message = "Use at least 8 characters.";

      if (wrapper) {
        wrapper.classList.toggle("invalid", Boolean(message));
        const error = wrapper.querySelector(".field-error");
        if (error) error.textContent = message;
      }
      if (message) valid = false;
    });
    if (!valid) toast("Check your form", "Please correct the highlighted fields and try again.", "error");
    return valid;
  }

  document.querySelectorAll("form[data-validate-only]").forEach((form) => {
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      if (!validateForm(form)) return;
      toast("Looks good", "Form validation passed. Connect this form to your backend endpoint to submit.", "success");
    });
  });

  document.querySelectorAll("[data-api-action]").forEach((button) => {
    button.addEventListener("click", async () => {
      const method = (button.dataset.apiAction || "post").toLowerCase();
      const path = button.dataset.apiPath;
      const original = button.innerHTML;
      if (!path || typeof window.EventEaseAPI[method] !== "function") {
        toast("Action unavailable", "This action is not configured yet.", "error");
        return;
      }
      button.disabled = true;
      button.textContent = "Please wait…";
      try {
        const result = await window.EventEaseAPI[method](path);
        const isEventRegistration = method === "post" && /\/api\/events\/\d+\/register$/.test(path);
        toast(
          isEventRegistration ? "Registered" : "Success",
          isEventRegistration ? "You are registered for this event." : (result && result.message ? result.message : "Your request has been completed."),
          "success"
        );
        button.textContent = button.dataset.successText || (isEventRegistration ? "Registered" : (method === "delete" ? "Cancelled" : "Request sent"));
        if (isEventRegistration) button.disabled = true;
        if (method === "delete") {
          const registrationId = path.split("/").pop();
          const trigger = document.querySelector(`[data-registration-id="${registrationId}"]`);
          const row = trigger?.closest("tr");
          if (row) {
            row.dataset.status = "CANCELLED";
            const badge = row.querySelector(".badge");
            if (badge) {
              badge.textContent = "Cancelled";
              badge.classList.remove("badge-registered");
              badge.classList.add("badge-cancelled");
            }
            trigger.replaceWith(document.createTextNode("Cancelled"));
          }
        }
      } catch (error) {
        toast("Could not complete request", error.message || "Please try again.", "error");
        button.disabled = false;
        button.innerHTML = original;
      }
    });
  });

  const eventSearch = document.querySelector("[data-event-search]");
  const eventCards = [...document.querySelectorAll("[data-event-card]")];
  const statusFilter = document.querySelector("[data-status-filter]");
  const dateFilter = document.querySelector("[data-date-filter]");
  function filterEvents() {
    const query = (eventSearch?.value || "").trim().toLowerCase();
    const status = statusFilter?.value || "";
    let visibleCount = 0;
    eventCards.forEach((card) => {
      const matchesText = !query || card.textContent.toLowerCase().includes(query);
      const matchesStatus = !status || card.dataset.status === status;
      const matchesDate = !dateFilter?.value || (card.dataset.date || "").startsWith(dateFilter.value);
      const show = matchesText && matchesStatus && matchesDate;
      card.hidden = !show;
      if (show) visibleCount += 1;
    });
    const empty = document.querySelector("[data-no-events]");
    if (empty) empty.hidden = visibleCount !== 0;
  }
  eventSearch?.addEventListener("input", filterEvents);
  statusFilter?.addEventListener("change", filterEvents);
  dateFilter?.addEventListener("change", filterEvents);
  document.querySelectorAll("[data-clear-filters]").forEach((button) => {
    button.addEventListener("click", () => {
      if (eventSearch) eventSearch.value = "";
      if (statusFilter) statusFilter.value = "";
      if (dateFilter) dateFilter.value = "";
      filterEvents();
    });
  });

  const participantSearch = document.querySelector("[data-participant-search]");
  const participantStatus = document.querySelector("[data-participant-status]");
  function filterParticipants() {
    const query = (participantSearch?.value || "").toLowerCase().trim();
    const status = participantStatus?.value || "";
    document.querySelectorAll("[data-participant-row]").forEach((row) => {
      row.hidden = !row.textContent.toLowerCase().includes(query) || (status && row.dataset.status !== status);
    });
  }
  participantSearch?.addEventListener("input", filterParticipants);
  participantStatus?.addEventListener("change", filterParticipants);

  const tableSearch = document.querySelector("[data-table-search]");
  const tableStatus = document.querySelector("[data-table-status]");
  function filterTable() {
    const query = (tableSearch?.value || "").toLowerCase().trim();
    const status = tableStatus?.value || "";
    document.querySelectorAll("[data-table-row]").forEach((row) => {
      row.hidden = !row.textContent.toLowerCase().includes(query) || (status && row.dataset.status !== status);
    });
  }
  tableSearch?.addEventListener("input", filterTable);
  tableStatus?.addEventListener("change", filterTable);

})();
