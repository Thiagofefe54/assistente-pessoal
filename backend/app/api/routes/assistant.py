from uuid import UUID
from fastapi import APIRouter, Depends, Request
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
    return day_plan(owner,request.headers['authorization'],datetime.now(ZoneInfo(body.timezone)).date())


@router.post('/demo')
def demo(body:ZoneRequest,request:Request,owner:UUID=Depends(current_user)):
    return seed_demo(owner,request.headers['authorization'],datetime.now(ZoneInfo(body.timezone)).date(),body.timezone)
