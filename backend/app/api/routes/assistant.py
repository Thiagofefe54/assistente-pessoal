from uuid import UUID
from fastapi import APIRouter, Depends, Request, Response
from backend.app.core.auth import current_user
from backend.app.api.routes.reports import ZoneRequest
from backend.app.core.assistant_panel import day_panel
from backend.app.core.poe_balance import balance
from datetime import date, datetime
from zoneinfo import ZoneInfo
from pydantic import Field, model_validator
from backend.app.core.recall import recall
from backend.app.core.day_plan import day_plan
from backend.app.core.demo import seed_demo
from backend.app.core import companion
from typing import Literal
from backend.app.core import banking


class SearchRequest(ZoneRequest):
    query: str = Field(default='',max_length=120)
    start: date | None = None
    end: date | None = None
    @model_validator(mode='after')
    def valid_window(self):
        if self.start and self.end and (self.start>self.end or (self.end-self.start).days>365):
            raise ValueError('Escolha um período de até 366 dias.')
        return self

router = APIRouter(prefix='/assistant', tags=['Meu dia'])


@router.post('/bank-status')
def bank_status(body:ZoneRequest,response:Response,owner:UUID=Depends(current_user)):
    response.headers['Cache-Control']='no-store'
    return banking.status(owner)


@router.post('/bank-summary')
def bank_summary(body:ZoneRequest,response:Response,owner:UUID=Depends(current_user)):
    response.headers['Cache-Control']='no-store'
    return banking.summary(owner)


class CheckinRequest(ZoneRequest):
    request_id:UUID
    category:Literal['work','gym','study','home','sleep','other']
    phase:Literal['start','finish','note']='note'
    note:str=Field(default='',max_length=2000)
    record_id:UUID|None=None
    expected_updated_at:str|None=Field(default=None,max_length=80)
    test_data:bool=False
    @model_validator(mode='after')
    def finish_identity(self):
        if self.phase=='finish' and (not self.record_id or not self.expected_updated_at):raise ValueError('Escolha um início para finalizar.')
        if self.phase!='finish' and (self.record_id or self.expected_updated_at):raise ValueError('Identidade inesperada.')
        return self


class TaskActionRequest(ZoneRequest):
    request_id:UUID
    task_id:UUID
    expected_updated_at:str=Field(min_length=1,max_length=80)
    action:Literal['reschedule','complete']
    target_date:date|None=None
    @model_validator(mode='after')
    def date_required(self):
        if (self.action=='reschedule')!=(self.target_date is not None):raise ValueError('Confira a data da ação.')
        return self


class UndoRequest(ZoneRequest):
    request_id:UUID
    target_kind:Literal['task','record']


@router.post('/review')
def daily_review(body:ZoneRequest,request:Request,owner:UUID=Depends(current_user)):
    return companion.review(owner,request.headers['authorization'],body.timezone)


@router.post('/checkin')
def checkin(body:CheckinRequest,request:Request,owner:UUID=Depends(current_user)):
    return companion.checkin(owner,request.headers['authorization'],body)


@router.post('/task-action')
def task_action(body:TaskActionRequest,request:Request,owner:UUID=Depends(current_user)):
    return companion.task_action(owner,request.headers['authorization'],body)


@router.post('/undo')
def undo(body:UndoRequest,request:Request,owner:UUID=Depends(current_user)):
    return companion.undo(request.headers['authorization'],body)


@router.post('/day')
def day(body: ZoneRequest, request: Request, owner: UUID = Depends(current_user)):
    return day_panel(owner, request.headers['authorization'], body.timezone)


@router.get('/usage')
def usage(owner: UUID = Depends(current_user)):
    return balance()


@router.post('/search')
def search(body:SearchRequest,request:Request,owner:UUID=Depends(current_user)):
    today=datetime.now(ZoneInfo(body.timezone)).date()
    return recall(owner,request.headers['authorization'],body.query,today,body.start,body.end)


@router.post('/plan')
def plan(body:ZoneRequest,request:Request,owner:UUID=Depends(current_user)):
    return day_plan(owner,request.headers['authorization'],datetime.now(ZoneInfo(body.timezone)).date(),body.timezone)


@router.post('/demo')
def demo(body:ZoneRequest,request:Request,owner:UUID=Depends(current_user)):
    return seed_demo(owner,request.headers['authorization'],datetime.now(ZoneInfo(body.timezone)).date(),body.timezone)
