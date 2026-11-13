# Per-IP concurrent connection limit (anti slow-attack / conn exhaustion)
limit_conn conn_per_ip 50;
# Reject well-known scanner user agents
if ($bad_bot) {
    return 403;
}
