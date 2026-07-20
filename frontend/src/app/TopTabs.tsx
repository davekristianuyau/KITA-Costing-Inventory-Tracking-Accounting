// Top tabs — one tab per service (contracts/navigation.md). URL-driven: the active tab is the :service
// route param; selecting a tab navigates to /app/:service. Radix Tabs gives roving focus + arrow-key nav.
import { useNavigate, useParams } from "react-router-dom";
import { Tabs, TabsList, TabsTrigger } from "../ui/Tabs";
import Icon from "../ui/Icon";
import { registry } from "../services/registry";

export default function TopTabs() {
  const navigate = useNavigate();
  const { service } = useParams();
  const active = registry.some((s) => s.id === service) ? service : registry[0].id;

  return (
    <Tabs value={active} onValueChange={(id) => navigate(`/app/${id}`)}>
      <TabsList aria-label="Services" className="overflow-x-auto">
        {registry.map((svc) => (
          <TabsTrigger key={svc.id} value={svc.id}>
            <Icon name={svc.icon} size={16} />
            <span className="whitespace-nowrap">{svc.label}</span>
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}
