from datetime import datetime, timezone
from typing import Literal
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from pydantic import BaseModel, Field, field_validator, model_validator

ActionType = Literal["complete", "defer", "snooze", "dismiss", "missed", "acknowledge", "custom"]
Priority = Literal["normal", "high", "critical"]
Delivery = Literal["alarm", "notification"]


def to_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        raise ValueError("timestamp must include a timezone offset")
    return value.astimezone(timezone.utc).replace(microsecond=0)


def fmt(value: datetime) -> str:
    return to_utc(value).strftime("%Y-%m-%dT%H:%M:%SZ")


class Action(BaseModel):
    action_id: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_.-]+$")
    label: str = Field(min_length=1, max_length=40)
    type: ActionType
    duration_min: int | None = Field(default=None, ge=1, le=1440)
    result_state: Literal["resolved", "cancelled"] | None = None
    outcome: str | None = Field(default=None, max_length=32)
    cancels_group: bool = False
    result_message: str | None = Field(default=None, max_length=200)

    @model_validator(mode="after")
    def duration_for_timed_actions(self):
        if self.type in ("defer", "snooze") and self.duration_min is None:
            raise ValueError(f"{self.type} actions need duration_min")
        return self


def _check_actions(actions: list[Action]) -> list[Action]:
    ids = [a.action_id for a in actions]
    if len(ids) != len(set(ids)):
        raise ValueError("action_id values must be unique within a job")
    return actions


def _check_tz(value: str) -> str:
    try:
        ZoneInfo(value)
    except (ZoneInfoNotFoundError, ValueError, OSError):
        raise ValueError(f"unknown timezone: {value}")
    return value


class JobIn(BaseModel):
    job_id: str | None = Field(default=None, min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_.:-]+$")
    routine_id: str | None = Field(default=None, max_length=64)
    category: str = Field(default="generic", min_length=1, max_length=32)
    title: str = Field(min_length=1, max_length=120)
    message: str = Field(default="", max_length=1000)
    fire_at: datetime
    tz: str = "UTC"
    expires_at: datetime | None = None
    priority: Priority = "normal"
    delivery: Delivery = "alarm"
    sound_profile: str = Field(default="default", max_length=32)
    actions: list[Action] = Field(min_length=1, max_length=6)
    cancel_group: str | None = Field(default=None, max_length=64)

    @field_validator("fire_at", "expires_at")
    @classmethod
    def utc_times(cls, v):
        return None if v is None else to_utc(v)

    @field_validator("tz")
    @classmethod
    def valid_tz(cls, v):
        return _check_tz(v)

    @field_validator("actions")
    @classmethod
    def unique_actions(cls, v):
        return _check_actions(v)

    @model_validator(mode="after")
    def expiry_after_fire(self):
        if self.expires_at is not None and self.expires_at <= self.fire_at:
            raise ValueError("expires_at must be after fire_at")
        return self


class JobPatch(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=120)
    message: str | None = Field(default=None, max_length=1000)
    fire_at: datetime | None = None
    tz: str | None = None
    expires_at: datetime | None = None
    priority: Priority | None = None
    delivery: Delivery | None = None
    sound_profile: str | None = Field(default=None, max_length=32)
    actions: list[Action] | None = Field(default=None, min_length=1, max_length=6)
    cancel_group: str | None = Field(default=None, max_length=64)

    @field_validator("fire_at", "expires_at")
    @classmethod
    def utc_times(cls, v):
        return None if v is None else to_utc(v)

    @field_validator("tz")
    @classmethod
    def valid_tz(cls, v):
        return None if v is None else _check_tz(v)

    @field_validator("actions")
    @classmethod
    def unique_actions(cls, v):
        return None if v is None else _check_actions(v)


class RegisterIn(BaseModel):
    pairing_code: str = Field(min_length=4, max_length=32)
    name: str = Field(min_length=1, max_length=64)


class ResponseIn(BaseModel):
    response_id: str = Field(min_length=8, max_length=64, pattern=r"^[A-Za-z0-9_.:-]+$")
    job_id: str = Field(min_length=1, max_length=64)
    action_id: str = Field(min_length=1, max_length=64)
    responded_at: datetime
    note: str | None = Field(default=None, max_length=500)

    @field_validator("responded_at")
    @classmethod
    def utc_time(cls, v):
        return to_utc(v)


class ResponsesIn(BaseModel):
    responses: list[ResponseIn] = Field(min_length=1, max_length=200)


class AckIn(BaseModel):
    received: list[str] = Field(default_factory=list, max_length=500)
    fired: list[str] = Field(default_factory=list, max_length=500)


class ResolveIn(BaseModel):
    outcome: str = Field(pattern=r"^[a-z_]{1,32}$")
    note: str | None = Field(default=None, max_length=500)
