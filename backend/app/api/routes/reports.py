from datetime import date
from typing import Literal
from uuid import UUID
from fastapi import APIRouter,Depends,Request
from pydantic import BaseModel,ConfigDict,Field,field_validator
from zoneinfo import ZoneInfo
from backend.app.core.auth import current_user
from backend.app.core.reports import period_report,due_reports,path,cloud

router=APIRouter(prefix='/reports',tags=['Relatórios'])

class ZoneRequest(BaseModel):
    model_config=ConfigDict(extra='forbid')
    timezone:str=Field(default='America/Sao_Paulo',max_length=100)
    @field_validator('timezone')
    @classmethod
    def zone(cls,value):
        try: ZoneInfo(value)
        except (ValueError,KeyError): raise ValueError('Fuso inválido') from None
        return value

class ReportRequest(ZoneRequest):
    kind:Literal['week','month','halfyear','year']
    anchor:date

@router.post('/read')
def read(body:ReportRequest,request:Request,owner:UUID=Depends(current_user)):
    return period_report(owner,request.headers['authorization'],body.kind,body.anchor,body.timezone)

@router.post('/prepare')
def prepare(body:ReportRequest,request:Request,owner:UUID=Depends(current_user)):
    return period_report(owner,request.headers['authorization'],body.kind,body.anchor,body.timezone,True)

@router.post('/due')
def due(request:Request,body:ZoneRequest|None=None,owner:UUID=Depends(current_user)):
    return due_reports(owner,request.headers['authorization'],body.timezone if body else 'America/Sao_Paulo')

@router.delete('/{kind}/{anchor}')
def delete(kind:Literal['week','month','halfyear','year'],anchor:date,request:Request,owner:UUID=Depends(current_user)):
    from backend.app.core.reports import period
    start,_=period(kind,anchor)
    cloud(path(owner,kind,start),request.headers['authorization'],'DELETE')
    return {'deleted':True}
