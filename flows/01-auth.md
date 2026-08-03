# Flow 01 — Authentication

Scope: first visit, signup, login, session persistence, expiry, logout, route protection.

Related issues: [I-5](ISSUES.md#i-5), [I-7](ISSUES.md#i-7), [I-10](ISSUES.md#i-10),
[I-11](ISSUES.md#i-11).

---

## 1.1 First visit while signed out

| | |
|---|---|
| **Steps** | Open `http://localhost:5173/` with empty localStorage |
| **Expected** | Either a public landing page or a redirect to sign-in |
| **Actual** | Redirects to `/login`. Split layout: marketing panel ("Connect with the rhythm of Ghana.") beside the form |
| **Result** | **Pass** |

Note — there is no public browsing at all. A first-time visitor cannot see a single track
before creating an account. For a marketplace that may be a deliberate product decision, but
it is worth confirming: it removes any chance of discovery-driven signup.

---

## 1.2 Empty form submit

| | |
|---|---|
| **Steps** | On `/login`, click **Log in** with both fields blank |
| **Expected** | Button disabled, or inline validation |
| **Actual** | Button is `disabled` — nothing happens, no request fired |
| **Result** | **Pass** |

---

## 1.3 Wrong password

| | |
|---|---|
| **Steps** | Valid email, password `WrongPassword123`, submit |
| **Expected** | A clear credential error |
| **Actual** | `POST /v1/auth/login → 401`, red inline message **"Incorrect email or password."** below the password field |
| **Result** | **Pass** |

---

## 1.4 Malformed email — **FAIL**

| | |
|---|---|
| **Steps** | Email `not-an-email`, password `x`, submit |
| **Expected** | "Please enter a valid email address" — ideally before any request |
| **Actual** | The button **enables** despite the invalid format. `POST /v1/auth/login → 422` (backend: "A valid email address is required"), but the UI shows **"Incorrect email or password."** |
| **Result** | **Fail** — see [I-7](ISSUES.md#i-7) |

The user is told their credentials are wrong when the real problem is the email format.

---

## 1.5 Successful login

| | |
|---|---|
| **Steps** | Sign in as the fan QA account (see README) |
| **Expected** | Redirect into the app, session stored |
| **Actual** | Redirects to `/`, token in `localStorage.beatzclik-token`, home renders **"Good evening, Kofi"** with the correct `KM` monogram |
| **Result** | **Pass** |

---

## 1.6 Signup

| | |
|---|---|
| **Steps** | `/signup` → name, email, 13-character password → **Get started** |
| **Expected** | Account created and signed in |
| **Actual** | Account created, redirected to `/` as **"Good evening, QA"**, session stored |
| **Result** | **Pass** |

**Weak password:** with a 3-character password the **Get started** button is `disabled`, so
the form cannot be submitted. Correct behaviour, though the only hint is the placeholder
text "Minimum 8 characters" — there is no message explaining why the button is inert.

---

## 1.7 Session expiry — **FAIL**

| | |
|---|---|
| **Steps** | Sign in, then let the token expire (**TTL = 900s**) or invalidate it; navigate to `/library` |
| **Expected** | Silent refresh, or a redirect that explains itself and returns the user to `/library` afterwards |
| **Actual** | Token cleared, redirected to `/login`, **no message**. After signing back in the user lands on `/`, not `/library` |
| **Result** | **Fail** — see [I-5](ISSUES.md#i-5) |

There is no refresh-token mechanism. `lib/api/client.ts:42-45` clears the session on any
401. With a 15-minute lifetime this fires repeatedly during normal use — including
mid-checkout and mid-release-wizard.

---

## 1.8 Logout

| | |
|---|---|
| **Steps** | `/settings` → scroll to the bottom → **Log out** |
| **Expected** | Session cleared, returned to login |
| **Actual** | Token removed, redirected to `/login` |
| **Result** | **Pass** |

Minor: **Log out** exists only at the very bottom of the Settings page. There is an
**Account** button in the header, but logout is not offered there, which is where most users
will look first.

---

## 1.9 Route protection

| | |
|---|---|
| **Steps** | Signed out, request `/library`, `/studio`, `/admin` directly |
| **Expected** | Redirect to login |
| **Actual** | All redirect to `/login` |
| **Result** | **Pass** |

Role separation was also confirmed at the API level — the fan token is refused by both
Studio and Admin:

| Role | `/me/collection` | `/studio/profile` | `/admin/settings` |
|---|---|---|---|
| Fan | 200 | **403** | **403** |
| Artist | 200 | 200 | **403** |
| Admin | 200 | **403** | 200 |

---

## 1.10 Password reset — **FAIL**

**"Forgot password?"** on the login form has `href="/login"`. Clicking it reloads the login
page. No reset route exists. See [I-10](ISSUES.md#i-10).

---

## Cross-cutting

**Tab title is `temp-app`** on every page — the scaffold default was never replaced. See
[I-11](ISSUES.md#i-11).

**Social login** — Facebook, Google and Twitter buttons appear on both forms. Not tested;
confirm with the team whether these are expected to work or are placeholders. If they are
placeholders they should be disabled, since a user who clicks one and gets nothing will
assume the app is broken.
