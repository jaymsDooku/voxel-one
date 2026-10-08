import Foundation

/// Tokens and passwords stay in process memory. URLSession uses normal system TLS trust.
@MainActor
final class Gateway {
    private let root:URL
    private let session:URLSession
    private var token=""
    private var sequence:Int64=0
    init(address:String) throws {
        guard let url=URL(string:address),let host=url.host,url.user==nil,url.password==nil,url.query==nil,url.fragment==nil,
              url.scheme=="https" || (url.scheme=="http" && ["127.0.0.1","localhost","::1"].contains(host)) else {throw GameError.invalidAddress}
        root=url
        let config=URLSessionConfiguration.ephemeral;config.timeoutIntervalForRequest=15;config.timeoutIntervalForResource=90;config.httpCookieStorage=nil;config.urlCache=nil
        session=URLSession(configuration:config)
    }
    private func post(_ route:String,_ body:[String:Any]) async throws -> Data {
        var request=URLRequest(url:root.appendingPathComponent("mobile/v1/\(route)"));request.httpMethod="POST"
        // Login includes TLS account handshake and the first full terrain snapshot.
        request.timeoutInterval = route=="login" ? 60 : 15
        request.httpBody=try JSONSerialization.data(withJSONObject:body);request.setValue("application/json",forHTTPHeaderField:"Content-Type")
        if !token.isEmpty {request.setValue("Bearer \(token)",forHTTPHeaderField:"Authorization")}
        let data:Data;let response:URLResponse
        do {(data,response)=try await session.data(for:request)}
        catch let error as URLError {
            // Fixed public messages contain no URL, account, token or raw transport detail.
            switch error.code {
            case .timedOut:throw GameError.message("Game request timed out. Try signing in again.")
            case .cannotConnectToHost,.cannotFindHost,.dnsLookupFailed:throw GameError.message("Cannot reach the game gateway.")
            case .networkConnectionLost,.notConnectedToInternet:throw GameError.message("Game network connection lost.")
            case .secureConnectionFailed,.serverCertificateUntrusted,.serverCertificateHasBadDate,.serverCertificateNotYetValid,.serverCertificateHasUnknownRoot:throw GameError.message("Game HTTPS certificate could not be verified.")
            default:throw GameError.message("Game network request failed.")
            }
        }
        guard data.count<=8_000_000 else {throw GameError.invalidWorld}
        guard let http=response as? HTTPURLResponse else {throw GameError.message("No game server response.")}
        guard http.statusCode==200 else {
            let error=(try? JSONSerialization.jsonObject(with:data)) as? [String:String]
            throw GameError.message(error?["error"] ?? "Game server unavailable.")
        }
        return data
    }
    func login(game:String,username:String,password:String,register:Bool) async throws -> Snapshot {
        struct Login:Decodable {var token:String;var state:Snapshot}
        let response=try JSONDecoder().decode(Login.self,from:await post("login",["game":game,"username":username,"password":password,"register":register]))
        try response.state.validate();token=response.token;return response.state
    }
    private func pose(_ world:VoxelWorld)->[String:Any] {
        sequence+=1
        return ["x":world.position.x,"y":world.position.y,"z":world.position.z,"yaw":world.yaw,"pitch":world.pitch,"sequence":sequence]
    }
    func move(_ world:VoxelWorld) async throws {
        _ = try await post("move",pose(world))
    }
    func state(_ world:VoxelWorld,focus:SCNFocus?) async throws -> Snapshot {
        var request=pose(world)
        if let focus=focus {request["focusX"]=focus.x;request["focusZ"]=focus.z}
        let result=try JSONDecoder().decode(Snapshot.self,from:await post("state",request));try result.validate();return result
    }
    func action(_ request:[String:Any]) async throws -> Snapshot {
        let result=try JSONDecoder().decode(Snapshot.self,from:await post("action",request));try result.validate();return result
    }
    func logout() async { _ = try? await post("logout",[:]);token="";session.invalidateAndCancel() }
}
struct SCNFocus {var x:Float;var z:Float}
