import * as restate from "@restatedev/restate-sdk";

const jepsenService = restate.object({
	name: "JepsenService",
	handlers: {

		read: async (ctx: restate.ObjectContext) => {
			const value = (await ctx.get<number>("value")) ?? 0;
			return {value: value};
		},

		write: async (ctx: restate.ObjectContext, request: number) => {
			ctx.set("value", request);
		},

		cas: async (ctx: restate.ObjectContext, request: {compare: number, exchange: number}) => {
			const value = (await ctx.get<number>("value")) ?? 0;
			if (request.compare === value) {
				ctx.set("value", request.exchange);
				return {success: true};
			} else {
				return {success: false};
			}
		}
	}
});

restate
  .endpoint()
  .bind(jepsenService)
  .listen();
