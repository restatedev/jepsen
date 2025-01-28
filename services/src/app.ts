import * as restate from "@restatedev/restate-sdk";

const get = async (ctx: restate.ObjectContext) => {
  return await ctx.get("value");
};

const set = async (ctx: restate.ObjectContext, value: object) => {
  ctx.console.log({
    msg: "Set value",
    newValue: value,
  });
  ctx.set("value", value);
};

const clear = async (ctx: restate.ObjectContext) => {
  ctx.clear("value");
};

const cas = async (ctx: restate.ObjectContext, request: { expected: object; newValue: object }) => {
  const currentValue = await ctx.get("value");
  if (currentValue === request.expected) {
    ctx.console.log({
      msg: "Compare-and-Set value",
      oldValue: currentValue,
      expected: request.expected,
      newValue: request.newValue,
    });
    ctx.set("value", request.newValue);
  } else {
    ctx.console.log({ msg: "Compare-and-Set precondition failed", currentValue, expected: request.expected });
    throw new restate.TerminalError("Precondition failed", { errorCode: 412 });
  }
};

const add = async (ctx: restate.ObjectContext, value: number) => {
  ctx.console.log({
    msg: "Append entry",
    value,
  });
  let stored = (await ctx.get("value") ?? []) as number[];
  let set = new Set(stored);
  if (!set.has(value)) {
    stored.push(value);
    ctx.set("value", stored);
  }
};

restate
  .endpoint()
  .bind(
    restate.object({
      name: "Register",
      handlers: { get, set, cas, clear },
    }),
  )
  .bind(
    restate.object({
      name: "Set",
      handlers: { get, set, add, clear },
    }),
  )
  .listen(9080);
