create unique index users_recovery_email_idx
  on users(lower(recovery_email)) where recovery_email is not null;
