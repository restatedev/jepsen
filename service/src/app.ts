import * as restate from "@restatedev/restate-sdk";

import {
  JepsenService,
  WriteRequest,
  WriteResponse,
  ReadRequest,
  ReadResponse,
  CasRequest,
  CasResponse,
  protoMetadata,
} from "./generated/proto/example";


export class MyExampleService implements JepsenService {

	async read(request: ReadRequest): Promise<ReadResponse> {
		const ctx = restate.useContext(this);
		const value = (await ctx.get<number>("value")) || 0;
		return ReadResponse.create({value: value});
	}


	async write(request: WriteRequest): Promise<WriteResponse> {
		const ctx = restate.useContext(this);
		ctx.set("value", request.value);
		return WriteResponse.create({});
	}


	async cas(request: CasRequest): Promise<CasResponse> {
		const ctx = restate.useContext(this);
		const value = (await ctx.get<number>("value")) || 0;
		if (request.compare === value) {
		     ctx.set("value", request.exchange);
			return CasResponse.create({success: true});
		} else {
			return CasResponse.create({success: false});
		}
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
