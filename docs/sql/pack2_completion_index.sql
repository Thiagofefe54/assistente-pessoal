-- Covers owner/task foreign key checks when deleting or undoing a task.
create index koi_completions_owner_task on public.koi_task_completions(user_id,task_id);
