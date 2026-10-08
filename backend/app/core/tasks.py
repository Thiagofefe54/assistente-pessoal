"""Read bounded live task context; actions are handled separately with receipts."""
from datetime import date, datetime, timedelta
from urllib.parse import urlencode
from uuid import UUID
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
import re

from fastapi import HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from backend.app.core.journal import cloud


class TaskProposal(BaseModel):
    model_config = ConfigDict(extra='forbid', strict=True)
    title: str = Field(min_length=1, max_length=160)
    notes: str = Field(max_length=2000)
    due_date: str | None
    due_time: str | None
    recurrence: str

    @field_validator('title')
    @classmethod
    def clean_title(cls, value):
        if not value.strip():
            raise ValueError('Empty title')
        return value.strip()

    @field_validator('due_time',mode='before')
    @classmethod
    def minute_precision(cls,value):
        if isinstance(value,str) and re.fullmatch(r'([01]\d|2[0-3]):[0-5]\d:00',value):
            return value[:5]
        return value

    @model_validator(mode='after')
    def valid_schedule(self):
        if self.due_date is not None:
            if not re.fullmatch(r'\d{4}-\d{2}-\d{2}', self.due_date):
                raise ValueError('Invalid date')
            date.fromisoformat(self.due_date)
        if self.due_time is not None and not re.fullmatch(r'([01]\d|2[0-3]):[0-5]\d', self.due_time):
            raise ValueError('Invalid time')
        if self.recurrence not in ('none', 'daily', 'weekly', 'monthly'):
            raise ValueError('Invalid recurrence')
        if (self.due_time is not None or self.recurrence != 'none') and self.due_date is None:
            raise ValueError('Date required')
        return self


def task_context(owner: UUID, authorization: str, timezone: str, message: str, requested_at: datetime | None = None) -> dict:
    try:
        now = (requested_at or datetime.now(ZoneInfo(timezone))).astimezone(ZoneInfo(timezone))
        today = now.date()
    except (ValueError, ZoneInfoNotFoundError):
        raise HTTPException(422, 'Escolha um fuso horário válido.') from None
    rows = []
    for offset in range(0, 500, 100):
        page = cloud('koi_tasks?' + urlencode({'user_id': 'eq.' + str(owner),
            'select': 'id,title,due_date,due_time,timezone,recurrence,completed_at,updated_at,archived_at',
            'order': 'slot.asc', 'limit': 100, 'offset': offset}), authorization)
        if not isinstance(page, list) or len(page) > 100:
            raise HTTPException(503, 'Não consegui conferir suas tarefas.')
        for row in page:
            if not isinstance(row, dict) or not isinstance(row.get('title'), str):
                raise HTTPException(503, 'Não consegui conferir suas tarefas.')
        rows.extend(page)
        if len(page) < 100:
            break
    # Keep inference bounded. The model sees explicit completeness/counts, never
    # assumes an omitted task is absent. Focus the snapshot on the requested day.
    lowered=message.lower()
    focus = today + timedelta(days=1 if 'amanhã' in lowered or 'amanha' in lowered else -1 if 'ontem' in lowered else 0)
    match = re.search(r'\b\d{4}-\d{2}-\d{2}\b', message)
    if match:
        try:
            focus = date.fromisoformat(match.group())
        except ValueError:
            pass
    brazilian=re.search(r'\b(\d{2})/(\d{2})/(\d{4})\b',message)
    if brazilian:
        try: focus=date(int(brazilian[3]),int(brazilian[2]),int(brazilian[1]))
        except ValueError: pass
    rows.sort(key=lambda r: (r['title'].casefold() not in message.casefold(),r.get('archived_at') is not None, r.get('due_date') != focus.isoformat(),
        r.get('completed_at') is not None, r.get('due_date') or '9999', r.get('due_time') or ''))
    selected = rows[:60]
    for r in selected:
        same=[t for t in rows if t['title'].casefold()==r['title'].casefold() and (t.get('completed_at') is None)==(r.get('completed_at') is None) and (t.get('archived_at') is None)==(r.get('archived_at') is None)]
        r['same_title_count']=len(same)
        r['same_title_date_count']=sum(t.get('due_date')==r.get('due_date') for t in same)
    return {'today': today.isoformat(), 'now': now.isoformat(), 'timezone': timezone, 'focus_date': focus.isoformat(),
        'total': len(rows), 'pending': sum(r.get('archived_at') is None and r.get('completed_at') is None for r in rows),
        'focus_pending': sum(r.get('archived_at') is None and r.get('completed_at') is None and r.get('due_date') == focus.isoformat() for r in rows),
        'complete_list': len(rows) <= 60, 'shown': len(selected), 'tasks': selected}
