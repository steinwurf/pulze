//
//  KeepAlivethread.swift
//  pulze
//
//  Created by Lars Nielsen on 19/01/2017.
//  Copyright © 2017 steinwurf. All rights reserved.
//

// Basing thread part on
// http://stackoverflow.com/a/24541470/936269

import Foundation

import CocoaAsyncSocket


class KeepAliveThread {
    
    let mAddress:String
    var PORT:Int = 13337
    var mInterval:Int
    var mTransmit:Bool = true
    
    // How to make interval const ?
    init(address:String, interval:Int)
    {
        mAddress = address
        mInterval = interval
    }
    
    func run()
    {
        NSLog("Starting keep alive...")
        var socket:GCDAsyncUdpSocket!

        //var error:NSError?
        
        socket = GCDAsyncUdpSocket(socketQueue: DispatchQueue.main)
        
        
        do
        {
            try socket.bind(toPort: UInt16(PORT))
            while(mTransmit)
            {
                var buffer:[UInt8] = {0x86}
                
                
            }
        } catch {
            // More fine grained error handling
            NSLog("Something went wrong")
        }
    }

}
