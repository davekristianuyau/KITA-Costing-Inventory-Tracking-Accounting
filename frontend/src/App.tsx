// App shell: /login (public) + the protected console under /app (contracts/navigation.md).
// /app redirects to the first service; /app/:service and /app/:service/:function render inside AppLayout.
import { Navigate, Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import AppLayout from "./app/AppLayout";
import ServiceView from "./app/ServiceView";
import ProtectedRoute from "./routes/ProtectedRoute";
import { firstServiceId } from "./services/registry";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/app"
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to={firstServiceId} replace />} />
        <Route path=":service" element={<ServiceView />} />
        <Route path=":service/:function" element={<ServiceView />} />
      </Route>
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
  );
}
