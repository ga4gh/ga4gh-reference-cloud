/* NOTE: THE FOLLOWING QUERIES ARE TEMPLATES, POPULATE USER IDS WITH THE USER IDS REGISTERED IN KRATOS (AND THE passport_user TABLE) */

/* THE FOLLOWING INSERT STATEMENT  */
INSERT INTO passport_user_visa_assertion (user_id, visa_id, current_status, current_status_at) VALUES
    ('<approved_user_id>', 'visa.ds.1', 'Approved', '2026-06-16 16:30:00'),
    ('<denied_user_id>', 'visa.ds.1', 'Denied', '2026-06-16 16:30:00');
