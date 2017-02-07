//
//  Packet.swift
//  pulze
//
//  Created by Lars Nielsen on 19/01/2017.
//  Copyright © 2017 steinwurf. All rights reserved.
//

import Foundation

class Packet {

    let LENGTH:Int = 22
    var mValid:Bool = false
    // Is this approach of assigning to 0 okay? 
    // Not done in the android version
    var mPacketNumber:Int = 0
    var mSenderInterval:Int = 0
    var mKeepAliveSignal:Int = 0
    
    init(buffer:[UInt8]) {
        
        
        var result = String(bytes: buffer, encoding: String.Encoding.utf8)!
        
        if (result.characters.count != LENGTH)
        {
            NSLog("result.length != LENGTH")
            return
        }
        
        let results:[String] = result.characters.split{$0 == ","}.map(String.init)
        
        if(results.count != 3)
        {
            NSLog("result.length != 3")
            return
        }
        
        self.mPacketNumber = Int(results[0])!
        self.mSenderInterval = Int(results[1])!
        self.mKeepAliveSignal = Int(results[2])!
        
        self.mValid = true
    }
}
