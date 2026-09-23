-- Owner's UPI collection id, shown in rent reminders as a payment link. Optional: a reminder
-- without one just asks the tenant to pay the owner directly, no link.
alter table pg_property add column upi_vpa text;
