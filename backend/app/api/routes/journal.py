from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, ConfigDict

from backend.app.core.auth import current_user
from backend.app.core.journal import daily_report, day_messages, drafts, report_path, cloud

router = APIRouter(prefix='/journal', tags=['Diário'])


class DayRequest(BaseModel):
    model_config = ConfigDict(extra='forbid')
    local_date: date


@router.get('/{day}')
def read_report(day: date, request: Request, owner: UUID = Depends(current_user)):
    return daily_report(owner, request.headers['authorization'], day)


@router.post('/summary')
def summarize(payload: DayRequest, request: Request, owner: UUID = Depends(current_user)):
    return daily_report(owner, request.headers['authorization'], payload.local_date, True)


@router.post('/suggestions')
def suggest(payload: DayRequest, request: Request, owner: UUID = Depends(current_user)):
    rows = day_messages(owner, request.headers['authorization'], payload.local_date)
    return {'items': drafts(rows, 'suggestions'), 'source_count': len(rows)}


@router.delete('/{day}')
def delete_report(day: date, request: Request, owner: UUID = Depends(current_user)):
    cloud(report_path(owner, day), request.headers['authorization'], 'DELETE')
    return {'deleted': True}
