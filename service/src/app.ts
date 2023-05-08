import * as restate from "@restatedev/restate-sdk";

import {
  JepsenService,
  WriteRequest,
  WriteResponse,
  ReadRequest,
  ReadResponse,
  protoMetadata,
} from "./generated/proto/example";


export class MyExampleService implements JepsenService {

	async read(request: ReadRequest): Promise<ReadResponse> {
		const ctx = restate.useContext(this);
		const value = (await ctx.get<number>(COUNTER_KEY)) || 0;
		return ReadResponse.create({value: value});
	}


	async write(request: WriteRequest): Promise<WriteResponse> {
		const ctx = restate.useContext(this);
		ctx.set("value", request.value);
		return WriteResponse.create({});
	}

}

restate
  .createServer()
  .bindService({
    descriptor: protoMetadata,
    service: "JepsenService",
    instance: new MyExampleService(),
  })
  .listen(8000);
